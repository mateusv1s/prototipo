package com.chatestoque;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ChatController {

    private final EstoqueService estoque    = new EstoqueService();
    private final RespostaService respostas = new RespostaService();

    @Autowired
    private HistoricoService historicoService;

    private SessaoChat getSessao(HttpSession httpSession) {
        SessaoChat sessao = (SessaoChat) httpSession.getAttribute("sessao");
        if (sessao == null) {
            sessao = new SessaoChat();
            List<SessaoChat.Mensagem> historicoPersistido = historicoService.carregar(httpSession.getId());
            for (SessaoChat.Mensagem m : historicoPersistido) {
                if ("usuario".equals(m.autor())) sessao.registrarUsuario(m.texto());
                else                             sessao.registrarBot(m.texto());
            }
            httpSession.setAttribute("sessao", sessao);
        }
        return sessao;
    }

    @GetMapping("/")
    public String index() {
        return "chat";
    }

    @PostMapping("/mensagem")
    @ResponseBody
    public String mensagem(@RequestParam String texto, HttpSession httpSession) {
        texto = texto.trim();
        SessaoChat sessao = getSessao(httpSession);
        String sessionId  = httpSession.getId();

        sessao.registrarUsuario(texto);
        historicoService.registrar(sessionId, "usuario", texto);

        String resposta = processar(texto, texto.toLowerCase(), sessao, httpSession);

        sessao.registrarBot(resposta);
        historicoService.registrar(sessionId, "bot", resposta);

        return resposta;
    }

    @GetMapping("/debug-historico")
    @ResponseBody
    public String debugHistorico(HttpSession httpSession) {
        try {
            String sessionId = httpSession.getId();
            historicoService.registrar(sessionId, "debug", "teste de escrita");
            return "OK! Arquivo criado.\nSession ID: " + sessionId
                    + "\nArquivo: historico_" + sessionId.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".csv"
                    + "\nPasta: " + historicoService.getCaminhoBase();
        } catch (Exception e) {
            return "ERRO: " + e.getMessage();
        }
    }

    private String processar(String texto, String t, SessaoChat sessao, HttpSession httpSession) {
        try {

            if (texto.isBlank()) return "Oi? Manda alguma coisa pra eu te ajudar!";

            detectarPreferenciaPagamento(t, sessao);

            if (isCancelar(t)) {
                resetarFluxo(sessao);
                return "Beleza, pedido cancelado! Qualquer coisa e so falar, parceiro.";
            }

            if (isEncerrar(t)) {
                historicoService.deletar(httpSession.getId());
                sessao.limparHistorico();
                httpSession.removeAttribute("sessao");
                return "Valeu pela visita, parceiro! Ate a proxima!";
            }

            if (!sessao.isCpfValidado()) {

                if (sessao.getEstado() == EstadoConversa.AGUARDANDO_COMANDO && isSaudacao(t)) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_NOME_USUARIO);
                    return "Eae! Seja bem-vindo(a)! Qual e o seu nome?";
                }

                if (sessao.getEstado() == EstadoConversa.AGUARDANDO_NOME_USUARIO) {
                    if (texto.length() < 2) return "Hmm, me manda seu nome completo pra continuar!";
                    sessao.setNomeUsuario(texto);
                    sessao.setEstado(EstadoConversa.AGUARDANDO_CPF);
                    return "Prazer, " + texto + "! Agora me manda seu CPF pra eu verificar.";
                }

                if (sessao.getEstado() == EstadoConversa.AGUARDANDO_CPF) {
                    if (CPFValidator.validar(t)) {
                        sessao.setCpfValidado(true);
                        sessao.setEstado(EstadoConversa.AGUARDANDO_COMANDO);
                        String nome = sessao.getNomeUsuario() != null ? sessao.getNomeUsuario() : "parceiro";
                        return "CPF verificado! Bora la, " + nome + "! O que vamos fazer hoje?\n\n" + menuTexto();
                    }
                    return "CPF invalido! Confere os digitos e manda de novo.";
                }

                sessao.setEstado(EstadoConversa.AGUARDANDO_NOME_USUARIO);
                return "Opa! Me manda seu nome pra comecar o atendimento.";
            }

            if (isMenu(t) && sessao.getEstado() == EstadoConversa.AGUARDANDO_COMANDO) {
                return "Claro! Ta na mao:\n\n" + menuTexto();
            }

            if (sessao.getEstado() == EstadoConversa.AGUARDANDO_CONFIRMACAO_ENDERECO) {

                if (isSimResposta(t)) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_PAGAMENTO);
                    return perguntarPagamento(sessao);
                }

                if (isNaoResposta(t)) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_ENDERECO);
                    return "Tranquilo! Me manda o novo endereco de entrega entao.";
                }

                if (contextoSugereSim(t, sessao)) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_PAGAMENTO);
                    return "Entendi que vai no mesmo endereco! " + perguntarPagamento(sessao);
                }

                if (contextoSugereNao(t, sessao)) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_ENDERECO);
                    return "Ok, me manda o novo endereco entao!";
                }

                return "Opa, nao entendi! Vai ser enviado pro mesmo endereco ("
                        + sessao.getEndereco() + ")? Responde sim ou nao!";
            }

            if (sessao.getEstado() == EstadoConversa.AGUARDANDO_ENDERECO) {

                if (ehPergunta(t)) {
                    return responderPerguntaContextual(t, sessao)
                            + "\n\nMas nao esquece: me manda o endereco de entrega pra continuar o pedido!";
                }

                if (texto.length() < 8) {
                    return "Eita, esse endereco parece curto demais! Manda completo pra mim (rua, numero, bairro).";
                }

                sessao.setEndereco(texto);
                sessao.setEstado(EstadoConversa.AGUARDANDO_PAGAMENTO);
                return "Anotado! " + perguntarPagamento(sessao);
            }

            if (sessao.getEstado() == EstadoConversa.AGUARDANDO_PAGAMENTO) {

                if (isSimResposta(t) && sessao.getPagamentoPreferido() != null) {
                    sessao.setPagamento(sessao.getPagamentoPreferido());
                    return finalizarPedido(sessao);
                }

                String pagamento = detectarPagamento(t);
                if (pagamento != null) {
                    sessao.setPagamento(pagamento);
                    if (sessao.getPagamentoPreferido() == null) sessao.setPagamentoPreferido(pagamento);
                    return finalizarPedido(sessao);
                }

                if (ehPergunta(t)) {
                    return responderPerguntaContextual(t, sessao)
                            + "\n\nMas me diz a forma de pagamento pra fechar o pedido! (PIX, cartao ou boleto)";
                }

                return "Hmm, nao entendi a forma de pagamento!\n\nAceitamos: PIX, cartao de credito, cartao de debito e boleto.\n\nQual prefere?";
            }

            if (t.equals("vou querer outra") || t.equals("repetir pedido")) {
                Produto ultimo = sessao.getUltimoProdutoComprado();
                if (ultimo == null) return "Eita, voce ainda nao comprou nada nessa sessao!";
                if (ultimo.getQuantidade() <= 0) ultimo.adicionar(1);
                ultimo.remover(1);
                return "Feito! Mais um(a) " + ultimo.getNome() + " " + ultimo.getTamanho()
                        + " " + ultimo.getCor() + " saindo!\nEstoque restante: " + ultimo.getQuantidade();
            }

            if (isIntencaoDeCompra(t) && sessao.getEstado() == EstadoConversa.AGUARDANDO_COMANDO) {

                String nomeProduto = extrairNomeProduto(t);
                String tamanho    = extrairTamanho(t);
                String cor        = extrairCor(t);
                int quantidade    = extrairQuantidade(t);

                sessao.setNomeProduto(nomeProduto);
                sessao.setCorProduto(cor.isEmpty() ? "sem cor" : cor);
                sessao.setQuantidadeDesejada(quantidade);
                sessao.setDescricaoOriginalPedido(quantidade + " " + nomeProduto
                        + (cor.isEmpty() ? "" : " " + cor));

                if (tamanho.isEmpty()) {
                    sessao.setEstado(EstadoConversa.AGUARDANDO_TAMANHO_COMPRA);
                    return "Qual o tamanho que voce quer? (PP, P, M, G, GG, XG, XGG)";
                }

                return avancarCompraComTamanho(tamanho, sessao);
            }

            if (sessao.getEstado() == EstadoConversa.AGUARDANDO_TAMANHO_COMPRA) {
                String tamanho = extrairTamanho(t);
                if (tamanho.isEmpty()) return "Nao reconheci esse tamanho! Usa: PP, P, M, G, GG, XG ou XGG.";
                return avancarCompraComTamanho(tamanho, sessao);
            }

            switch (sessao.getEstado()) {

                case AGUARDANDO_COMANDO:

                    if (t.equals("1") || t.contains("cadastrar")) {
                        sessao.setEstado(EstadoConversa.AGUARDANDO_ID);
                        return "Bora cadastrar! Me manda o ID do produto.";
                    }

                    if (t.equals("2") || t.contains("listar")) {
                        return listarProdutos();
                    }

                    if (t.equals("3") || t.contains("comprar")) {
                        return "Manda bala! Me diz o que quer comprar. Ex: \"quero 2 camisas GG pretas\"";
                    }

                    if (ehPergunta(t)) return responderPerguntaContextual(t, sessao);

                    if (isReclamacao(t)) return "Calma parceiro, to aqui pra te ajudar! Me diz o que precisa.";

                    if (isGiria(t)) return respostaGiria(t, sessao);

                    String inferida = tentarInferirIntencao(t, sessao);
                    if (inferida != null) return inferida;

                    return "Hmm, nao entendi bem! Voce quis dizer o que exatamente?\n\nPosso te ajudar com:\n" + menuTexto();

                case AGUARDANDO_ID:
                    if (!t.matches("\\d+")) return "Opa, o ID precisa ser um numero! Manda de novo:";
                    sessao.setIdProduto(Integer.parseInt(t));
                    sessao.setEstado(EstadoConversa.AGUARDANDO_NOME);
                    return "Boa! Agora me diz o nome do produto.";

                case AGUARDANDO_NOME:
                    sessao.setNomeProduto(t);
                    sessao.setEstado(EstadoConversa.AGUARDANDO_TAMANHO);
                    return "Certo! Qual o tamanho? (PP, P, M, G, GG, XG, XGG ou numero como 36, 38...)";

                case AGUARDANDO_TAMANHO:
                    String tam = texto.toUpperCase().trim();
                    boolean tamValido = tam.equals("PP") || tam.equals("P") || tam.equals("M")
                            || tam.equals("G") || tam.equals("GG") || tam.equals("XG")
                            || tam.equals("XGG") || tam.matches("\\d+");
                    if (!tamValido) return "Opa, tamanho invalido! Usa: PP, P, M, G, GG, XG, XGG ou numero (36, 38, 40...)";
                    sessao.setTamanhoProduto(tam);
                    sessao.setEstado(EstadoConversa.AGUARDANDO_COR);
                    return "Show! Qual a cor?";

                case AGUARDANDO_COR:
                    sessao.setCorProduto(t);
                    sessao.setEstado(EstadoConversa.AGUARDANDO_QUANTIDADE);
                    return "Massa! Agora me diz a quantidade em estoque.";

                case AGUARDANDO_QUANTIDADE:
                    if (!t.matches("\\d+")) return "Opa, quantidade precisa ser um numero! Tenta de novo:";
                    int qtd = Integer.parseInt(t);
                    if (qtd <= 0) return "Eita, quantidade tem que ser maior que zero!";
                    Produto novo = new Produto(sessao.getIdProduto(), sessao.getNomeProduto(),
                            sessao.getTamanhoProduto(), sessao.getCorProduto(), qtd);
                    estoque.listar().add(novo);
                    sessao.setEstado(EstadoConversa.AGUARDANDO_COMANDO);
                    sessao.setIdProduto(null); sessao.setNomeProduto(null);
                    sessao.setTamanhoProduto(null); sessao.setCorProduto(null); sessao.setQuantidade(null);
                    return "Produto cadastrado com sucesso, parceiro!\n\nID: " + novo.getId()
                            + "\nNome: " + novo.getNome() + "\nTamanho: " + novo.getTamanho()
                            + "\nCor: " + novo.getCor() + "\nQuantidade: " + novo.getQuantidade();

                default:
                    sessao.setEstado(EstadoConversa.AGUARDANDO_COMANDO);
                    return "Eita, deu ruim! Voltando pro menu...\n\n" + menuTexto();
            }

        } catch (NumberFormatException e) {
            return "Opa, precisa ser um numero ai! Tenta de novo.";
        } catch (Exception e) {
            return "Erro inesperado: " + e.getMessage();
        }
    }

    private String tentarInferirIntencao(String t, SessaoChat sessao) {
        String anterior = sessao.getMensagemAnteriorUsuario().toLowerCase();
        if (!anterior.isEmpty() && isIntencaoDeCompra(anterior)) {
            String nomeProduto = extrairNomeProduto(t);
            if (!nomeProduto.equals("produto")) {
                return "Entendi! Voce quer comprar " + nomeProduto + "? Me confirma: tamanho e cor que quer!";
            }
        }
        if (sessao.usuarioJaMencionou("listar") || sessao.usuarioJaMencionou("lista")
                || sessao.usuarioJaMencionou("produtos") || sessao.usuarioJaMencionou("tem ")) {
            return "Quer ver os produtos disponiveis? Aqui vai:\n\n" + listarProdutos();
        }
        return null;
    }

    private boolean contextoSugereSim(String t, SessaoChat sessao) {
        String enderecoSalvo = sessao.getEndereco() != null ? sessao.getEndereco().toLowerCase() : "";
        if (!enderecoSalvo.isEmpty() && t.contains(enderecoSalvo.substring(0, Math.min(5, enderecoSalvo.length())))) {
            return true;
        }
        return t.contains("mesmo") || t.contains("igual") || t.contains("pode") || t.contains("la mesmo");
    }

    private boolean contextoSugereNao(String t, SessaoChat sessao) {
        return t.contains("mudei") || t.contains("mudou") || t.contains("diferente")
                || t.contains("novo") || t.contains("outra") || t.contains("outro lugar");
    }

    private void detectarPreferenciaPagamento(String t, SessaoChat sessao) {
        if (sessao.getPagamentoPreferido() != null) return;
        boolean mencaoPreferencia = t.contains("prefiro") || t.contains("costumo")
                || t.contains("sempre uso") || t.contains("gosto de pagar");
        if (mencaoPreferencia) {
            String pag = detectarPagamento(t);
            if (pag != null) sessao.setPagamentoPreferido(pag);
        }
    }

    private String perguntarPagamento(SessaoChat sessao) {
        String preferido = sessao.getPagamentoPreferido();
        if (preferido != null) {
            return "Qual a forma de pagamento?\n\nAceitamos: PIX, cartao de credito, cartao de debito e boleto."
                    + "\n\n(Da ultima vez voce usou " + preferido + " - quer usar de novo?)";
        }
        return "Qual a forma de pagamento?\n\nAceitamos: PIX, cartao de credito, cartao de debito e boleto.";
    }

    private String responderPerguntaContextual(String t, SessaoChat sessao) {
        if (t.contains("pagamento") || t.contains("pagar") || t.contains("aceita") || t.contains("formas"))
            return "Aceitamos PIX, cartao de credito, cartao de debito e boleto.";
        if (t.contains("entrega") || t.contains("prazo") || t.contains("frete") || t.contains("demora"))
            return "O prazo varia conforme o endereco. Fala com nosso suporte pra mais detalhes!";
        if (t.contains("tem ") || t.contains("estoque") || t.contains("produto"))
            return "Temos camisas nos tamanhos P, M, G e GG nas cores rosa, preta e branca. Digita '2' pra ver tudo!";
        if (t.contains("cancelar") || t.contains("devolver") || t.contains("troca"))
            return "Pra cancelamentos e trocas, entra em contato com nosso suporte!";
        if (t.contains("horario") || t.contains("horário") || t.contains("funciona"))
            return "Funcionamos 24h por aqui no chat!";

        String estadoDesc;
        switch (sessao.getEstado()) {
            case AGUARDANDO_ENDERECO:  estadoDesc = "ainda preciso do seu endereco de entrega"; break;
            case AGUARDANDO_PAGAMENTO: estadoDesc = "ainda preciso da forma de pagamento"; break;
            default:                   estadoDesc = "posso te ajudar com compras, cadastro e listagem"; break;
        }
        return "Hmm, nao entendi bem! O que voce quis dizer?\n\n(Lembrando: " + estadoDesc + ")";
    }

    private static final double PRECO_UNITARIO = 59.90;

    private String avancarCompraComTamanho(String tamanho, SessaoChat sessao) {
        sessao.setTamanhoProduto(tamanho);
        sessao.setProdutoSelecionado(estoque.buscar(sessao.getNomeProduto(), tamanho, sessao.getCorProduto()));

        String desc = sessao.getQuantidadeDesejada() + " " + sessao.getNomeProduto()
                + " " + tamanho
                + ("sem cor".equals(sessao.getCorProduto()) ? "" : " " + sessao.getCorProduto());
        sessao.setDescricaoOriginalPedido(desc);

        double total = sessao.getQuantidadeDesejada() * PRECO_UNITARIO;
        String resumo = "Anotado! " + desc + "\n"
                + "Valor unitario: R$ " + String.format("%.2f", PRECO_UNITARIO) + "\n"
                + "Total: R$ " + String.format("%.2f", total) + "\n\n";

        if (sessao.getEndereco() != null && !sessao.getEndereco().isBlank()) {
            sessao.setEstado(EstadoConversa.AGUARDANDO_CONFIRMACAO_ENDERECO);
            return resumo + "Vai enviar pro mesmo endereco de antes?\n(" + sessao.getEndereco() + ")";
        }

        sessao.setEstado(EstadoConversa.AGUARDANDO_ENDERECO);
        return resumo + "Qual o endereco de entrega?";
    }

    private String finalizarPedido(SessaoChat sessao) {
        Produto p          = sessao.getProdutoSelecionado();
        int qtd            = sessao.getQuantidadeDesejada();
        String nome        = sessao.getNomeProduto()    != null ? sessao.getNomeProduto()    : "produto";
        String tam         = sessao.getTamanhoProduto() != null ? sessao.getTamanhoProduto() : "U";
        String cor         = sessao.getCorProduto()     != null ? sessao.getCorProduto()     : "sem cor";
        String nomeUsuario = sessao.getNomeUsuario()    != null ? sessao.getNomeUsuario()    : "parceiro";
        String desc        = sessao.getDescricaoOriginalPedido();
        double total       = qtd * PRECO_UNITARIO;

        if (p == null) {
            p = new Produto(estoque.listar().size() + 1, nome, tam, cor, qtd);
            estoque.listar().add(p);
        } else if (p.getQuantidade() < qtd) {
            p.adicionar(qtd - p.getQuantidade());
        }

        p.remover(qtd);
        sessao.setUltimoProdutoComprado(p);
        sessao.setUltimaQuantidadeComprada(qtd);
        sessao.setEstado(EstadoConversa.AGUARDANDO_COMANDO);

        StringBuilder conf = new StringBuilder("Pedido confirmado, " + nomeUsuario + "!\n\n");
        if (desc != null && !desc.isBlank()) conf.append("Produto: ").append(desc).append("\n");
        conf.append("Valor unitario: R$ ").append(String.format("%.2f", PRECO_UNITARIO)).append("\n")
                .append("Total: R$ ").append(String.format("%.2f", total)).append("\n")
                .append("Endereco: ").append(sessao.getEndereco()).append("\n")
                .append("Pagamento: ").append(sessao.getPagamento()).append("\n\n")
                .append("Valeu pela compra! Qualquer coisa e so falar.");

        sessao.setPagamento(null); sessao.setNomeProduto(null);
        sessao.setTamanhoProduto(null); sessao.setCorProduto(null);
        sessao.setProdutoSelecionado(null); sessao.setQuantidadeDesejada(0);
        sessao.setDescricaoOriginalPedido(null);

        return conf.toString();
    }

    private String listarProdutos() {
        if (estoque.listar().isEmpty()) return "Ainda nao tem nenhum produto cadastrado nao, parceiro.";
        StringBuilder sb = new StringBuilder("Olha o que temos aqui:\n\n");
        for (Produto p : estoque.listar()) {
            sb.append("ID: ").append(p.getId())
                    .append(" | ").append(p.getNome())
                    .append(" | ").append(p.getTamanho())
                    .append(" | ").append(p.getCor())
                    .append(" | Qtd: ").append(p.getQuantidade())
                    .append("\n");
        }
        return sb.toString();
    }

    private void resetarFluxo(SessaoChat sessao) {
        sessao.setEstado(EstadoConversa.AGUARDANDO_COMANDO);
        sessao.setEndereco(null); sessao.setPagamento(null);
        sessao.setNomeProduto(null); sessao.setTamanhoProduto(null);
        sessao.setCorProduto(null); sessao.setProdutoSelecionado(null);
        sessao.setQuantidadeDesejada(0);
    }

    private boolean isSaudacao(String t) {
        return t.equals("oi") || t.equals("ola") || t.equals("olá") || t.equals("bom dia")
                || t.equals("boa tarde") || t.equals("boa noite") || t.equals("eae") || t.equals("e ai")
                || t.equals("e aí") || t.equals("iae") || t.equals("fala") || t.equals("salve")
                || t.equals("opa") || t.equals("coe") || t.equals("hey") || t.equals("ei")
                || t.equals("tudo bem") || t.equals("tudo bom") || t.equals("qual e") || t.equals("qual é")
                || t.equals("bora") || t.equals("fala ai") || t.equals("fala aí") || t.equals("boa")
                || t.equals("slc") || t.equals("fala tu") || t.equals("oxe") || t.equals("uai")
                || t.equals("vim comprar") || t.equals("vim ver") || t.equals("saudacoes")
                || t.startsWith("bom dia") || t.startsWith("boa tarde") || t.startsWith("boa noite");
    }

    private boolean isEncerrar(String t) {
        return t.equals("sair") || t.equals("4") || t.contains("encerrar") || t.contains("fechar")
                || t.equals("tchau") || t.equals("flw") || t.equals("falou") || t.equals("ate mais")
                || t.equals("até mais") || t.equals("ate") || t.equals("até") || t.equals("xau")
                || t.equals("bye") || t.equals("vlw") || t.equals("abraco") || t.equals("abraço")
                || t.equals("fui") || t.equals("vou nessa") || t.equals("obrigado")
                || t.equals("obrigada") || t.equals("obg") || t.equals("tmj") || t.equals("foi")
                || t.equals("ok obrigado") || t.equals("ok obrigada");
    }

    private boolean isMenu(String t) {
        return t.contains("menu") || t.contains("ajuda") || t.contains("opcoes") || t.contains("opções")
                || t.equals("o que tem") || t.equals("como funciona") || t.contains("quais opcoes")
                || t.contains("quais opções") || t.contains("o que posso fazer");
    }

    private boolean isCancelar(String t) {
        return t.equals("cancelar") || t.equals("cancela") || t.equals("cancelo")
                || t.equals("desisto") || t.equals("esquece") || t.equals("esquece isso")
                || t.equals("deixa pra la") || t.equals("deixa pra lá")
                || t.equals("nao quero mais") || t.equals("não quero mais") || t.equals("chega");
    }

    private boolean isIntencaoDeCompra(String t) {
        return t.contains("quero") || t.contains("preciso") || t.contains("me manda")
                || t.contains("me mande") || t.contains("comprar") || t.contains("gostaria")
                || t.contains("vou querer") || t.contains("pedido") || t.contains("pedir")
                || t.contains("me vende") || t.contains("me passa") || t.contains("bora comprar")
                || t.contains("queria") || t.contains("to afim") || t.contains("tô afim")
                || t.contains("to querendo") || t.contains("tô querendo")
                || t.contains("manda pra mim") || t.contains("me da") || t.contains("me dá")
                || t.contains("me arruma") || t.contains("separar") || t.contains("reservar")
                || t.contains("pode me mandar") || t.contains("solicitar") || t.contains("adquirir");
    }

    private boolean isSimResposta(String t) {
        if (t.equals("sim") || t.equals("s") || t.equals("yes") || t.equals("yeah")
                || t.equals("yep") || t.equals("pode") || t.equals("pode ser") || t.equals("isso")
                || t.equals("aham") || t.equals("pdp") || t.equals("suave") || t.equals("mec")
                || t.equals("exato") || t.equals("correto") || t.equals("afirmativo")
                || t.equals("com certeza") || t.equals("claro") || t.equals("manda") || t.equals("vai")
                || t.equals("bora") || t.equals("positivo") || t.equals("isso mesmo") || t.equals("ok")
                || t.equals("okay") || t.equals("ta") || t.equals("tá") || t.equals("ta bom")
                || t.equals("tá bom") || t.equals("fechado") || t.equals("mesmo") || t.equals("igual")
                || t.equals("mesmo endereco") || t.equals("mesmo endereço") || t.equals("boa")
                || t.equals("massa")) return true;
        if (t.split("\\s+").length <= 3) {
            if (t.contains("sim") || t.contains("mesmo endereco") || t.contains("mesmo endereço")
                    || t.contains("pode mandar") || t.contains("manda la") || t.contains("manda lá"))
                return true;
        }
        return false;
    }

    private boolean isNaoResposta(String t) {
        return t.equals("nao") || t.equals("não") || t.equals("no") || t.equals("nope")
                || t.equals("negativo") || t.equals("outro") || t.equals("outro endereco")
                || t.equals("outro endereço") || t.equals("mudou") || t.equals("mudei") || t.equals("nop")
                || t.contains("endereco diferente") || t.contains("endereço diferente")
                || t.contains("outro lugar") || t.contains("endereco novo") || t.contains("endereço novo")
                || t.contains("novo endereco") || t.contains("novo endereço");
    }

    private boolean isReclamacao(String t) {
        return t.contains("pqp") || t.contains("que isso") || t.contains("absurdo")
                || t.contains("ridiculo") || t.contains("ridículo") || t.contains("nao funciona")
                || t.contains("não funciona") || t.contains("ta quebrado") || t.contains("hein")
                || t.contains("???");
    }

    private boolean isGiria(String t) {
        return t.equals("cara") || t.equals("mano") || t.equals("brother") || t.equals("bro")
                || t.equals("parceiro") || t.equals("chefe") || t.equals("vlw") || t.equals("valeu")
                || t.equals("tmj") || t.equals("tmb") || t.equals("salve") || t.equals("eita")
                || t.equals("nossa") || t.equals("caramba") || t.equals("po") || t.equals("pô")
                || t.equals("puts") || t.equals("poxa") || t.equals("boa") || t.equals("show")
                || t.equals("massa") || t.equals("irado") || t.equals("top") || t.equals("demais")
                || t.equals("dahora") || t.equals("d+") || t.equals("eh isso") || t.equals("é isso")
                || t.equals("certo") || t.equals("legal") || t.equals("blz") || t.equals("beleza")
                || t.equals("suave") || t.equals("tranquilo") || t.equals("foi") || t.equals("fui")
                || t.equals("bora") || t.equals("vamo") || t.equals("vamos") || t.equals("ae")
                || t.equals("aee") || t.equals("aeee") || t.equals("uau") || t.equals("wow")
                || t.equals("xi") || t.equals("xiii") || t.equals("ata") || t.equals("entendi")
                || t.equals("entao") || t.equals("então") || t.equals("lol") || t.equals("slc")
                || t.equals("oxe") || t.equals("uai") || t.equals("coe") || t.equals("so")
                || t.startsWith("kk") || t.startsWith("haha") || t.startsWith("rs") || t.startsWith("hehe");
    }

    private String respostaGiria(String t, SessaoChat sessao) {
        if (t.startsWith("kk") || t.startsWith("haha") || t.startsWith("hehe")
                || t.startsWith("rs") || t.equals("lol")) {
            return "Haha! Mas pode falar, to aqui. O que voce precisa?";
        }
        if (t.equals("vlw") || t.equals("valeu") || t.equals("tmj") || t.equals("obg")) {
            return "Imagina! Qualquer coisa e so chamar.";
        }
        if (t.equals("eita") || t.equals("nossa") || t.equals("caramba") || t.equals("uau")
                || t.equals("wow") || t.equals("slc") || t.equals("oxe") || t.equals("uai")) {
            if (sessao.usuarioJaMencionou("quero") || sessao.usuarioJaMencionou("comprar")
                    || sessao.usuarioJaMencionou("preciso")) {
                return "Eita! Teve algo que nao ficou certo? Pode falar que eu ajusto!";
            }
        }
        return "Pode falar! O que voce precisa?";
    }

    private boolean ehPergunta(String t) {
        return t.contains("?") || t.startsWith("qual") || t.startsWith("como") || t.startsWith("quando")
                || t.startsWith("onde") || t.startsWith("o que") || t.startsWith("quais")
                || t.startsWith("quanto") || t.startsWith("por que") || t.startsWith("tem ")
                || t.startsWith("voces") || t.startsWith("vocês") || t.startsWith("vcs")
                || t.startsWith("da pra") || t.startsWith("dá pra");
    }

    private String detectarPagamento(String t) {
        if (t.contains("pix"))                                            return "PIX";
        if (t.contains("credito") || t.contains("crédito"))              return "Cartao de credito";
        if (t.contains("debito") || t.contains("débito"))                return "Cartao de debito";
        if (t.contains("cartao") || t.contains("cartão"))                return "Cartao";
        if (t.contains("boleto"))                                         return "Boleto";
        if (t.contains("dinheiro") || t.contains("especie"))             return "Dinheiro";
        if (t.contains("transferencia") || t.contains("transferência"))  return "Transferencia";
        if (t.contains("vale"))                                           return "Vale";
        return null;
    }

    private String menuTexto() {
        return "1 - Cadastrar produto\n2 - Listar produtos\n3 - Comprar produto\n4 - Sair\n\nDigita o numero ou me fala direto o que quer!";
    }

    private String extrairNomeProduto(String texto) {
        for (Produto p : estoque.listar()) {
            if (texto.contains(p.getNome().toLowerCase())) return p.getNome().toLowerCase();
        }
        String[] ignorar = {"quero","preciso","me","manda","mande","comprar","gostaria","de","da","do",
                "um","uma","umas","uns","pedir","pedido","vou","por","favor","pra","para","e","com",
                "querer","agora","bora","to","to","afim","querendo","vende","passa","arruma",
                "separar","reservar","pode","solicitar","adquirir","da","mim","mais"};
        for (String palavra : texto.split("\\s+")) {
            if (palavra.matches("\\d+")) continue;
            boolean ignorada = false;
            for (String ig : ignorar) { if (palavra.equals(ig)) { ignorada = true; break; } }
            if (!ignorada && palavra.length() > 2) return palavra;
        }
        return "produto";
    }

    private String extrairTamanho(String texto) {
        if (texto.matches(".*\\bxgg\\b.*")) return "XGG";
        if (texto.matches(".*\\bxg\\b.*"))  return "XG";
        if (texto.matches(".*\\bgg\\b.*"))  return "GG";
        if (texto.matches(".*\\bpp\\b.*"))  return "PP";
        if (texto.matches(".*\\bg\\b.*"))   return "G";
        if (texto.matches(".*\\bm\\b.*"))   return "M";
        if (texto.matches(".*\\bp\\b.*"))   return "P";
        return "";
    }

    private String extrairCor(String texto) {
        if (texto.contains("rosa"))      return "rosa";
        if (texto.contains("preta"))     return "preta";
        if (texto.contains("branca"))    return "branca";
        if (texto.contains("azul"))      return "azul";
        if (texto.contains("verde"))     return "verde";
        if (texto.contains("vermelha"))  return "vermelha";
        if (texto.contains("amarela"))   return "amarela";
        if (texto.contains("cinza"))     return "cinza";
        if (texto.contains("laranja"))   return "laranja";
        if (texto.contains("roxa"))      return "roxa";
        if (texto.contains("bege"))      return "bege";
        return "";
    }

    private int extrairQuantidade(String texto) {
        for (String parte : texto.split("\\s+")) {
            if (parte.matches("\\d+")) {
                int v = Integer.parseInt(parte);
                if (v > 0) return v;
            }
        }
        return 1;
    }
}