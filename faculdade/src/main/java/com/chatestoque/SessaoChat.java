package com.chatestoque;

import java.util.ArrayList;
import java.util.List;

public class SessaoChat {

    // ------------------------------------------------------------------ //
    //  Histórico de conversa
    // ------------------------------------------------------------------ //

    // Classe interna simples no lugar de record — compatível com Java 17 sem --enable-preview.
    public static class Mensagem {
        private final String autor;
        private final String texto;

        public Mensagem(String autor, String texto) {
            this.autor = autor;
            this.texto = texto;
        }

        public String autor() { return autor; }
        public String texto() { return texto; }
    }

    // "usuario" ou "bot" como autor.
    // Limitamos a 30 mensagens (15 trocas) pra não crescer infinitamente.
    private final List<Mensagem> historico = new ArrayList<>();
    private static final int MAX_HISTORICO = 30;

    public void registrarUsuario(String texto) {
        adicionar("usuario", texto);
    }

    public void registrarBot(String texto) {
        adicionar("bot", texto);
    }

    private void adicionar(String autor, String texto) {
        if (historico.size() >= MAX_HISTORICO) {
            historico.remove(0);
        }
        historico.add(new Mensagem(autor, texto));
    }

    public List<Mensagem> getHistorico() {
        return historico;
    }

    // Retorna as últimas N mensagens do usuário (ignora as do bot).
    // Útil pra buscar intenções anteriores sem ruído.
    public List<String> getMensagensUsuario(int ultimas) {
        List<String> result = new ArrayList<>();
        for (int i = historico.size() - 1; i >= 0 && result.size() < ultimas; i--) {
            if (historico.get(i).autor().equals("usuario")) {
                result.add(0, historico.get(i).texto());
            }
        }
        return result;
    }

    // Verifica se alguma mensagem anterior do usuário contém o trecho informado.
    // Usado pra detectar preferências mencionadas antes (ex: "prefiro PIX").
    public boolean usuarioJaMencionou(String trecho) {
        for (Mensagem m : historico) {
            if (m.autor().equals("usuario") && m.texto().toLowerCase().contains(trecho.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // Retorna a última mensagem do usuário antes da atual (penúltima do usuário).
    // Útil pra entender o que ele queria quando mandou algo ambíguo.
    public String getMensagemAnteriorUsuario() {
        int count = 0;
        for (int i = historico.size() - 1; i >= 0; i--) {
            if (historico.get(i).autor().equals("usuario")) {
                count++;
                if (count == 2) return historico.get(i).texto();
            }
        }
        return "";
    }

    public void limparHistorico() {
        historico.clear();
    }

    // ------------------------------------------------------------------ //
    //  Estado e flags
    // ------------------------------------------------------------------ //

    private EstadoConversa estado = EstadoConversa.AGUARDANDO_COMANDO;
    private boolean cpfValidado = false;

    // Nome informado pelo usuário na autenticação.
    private String nomeUsuario;

    // Texto original que o usuário digitou ao pedir a compra.
    // Ex: "cara vou querer 50 camisas azul do brasil"
    // Guardamos pra repetir exatamente no resumo, sem filtrar nada.
    private String descricaoOriginalPedido;

    // ------------------------------------------------------------------ //
    //  Dados de cadastro de produto
    // ------------------------------------------------------------------ //

    private Integer idProduto;
    private String nomeProduto;
    private String tamanhoProduto;
    private String corProduto;
    private Integer quantidade;

    // ------------------------------------------------------------------ //
    //  Dados de compra
    // ------------------------------------------------------------------ //

    private Produto ultimoProdutoComprado;
    private int ultimaQuantidadeComprada = 1;
    private Produto produtoSelecionado;
    private int quantidadeDesejada;

    // ------------------------------------------------------------------ //
    //  Dados do pedido — mantidos entre compras da mesma sessão
    // ------------------------------------------------------------------ //

    private String endereco;
    private String pagamento;

    // Preferência de pagamento detectada no histórico (ex: usuário disse "costumo pagar no PIX").
    // Sugerida automaticamente na próxima compra.
    private String pagamentoPreferido;

    // ------------------------------------------------------------------ //
    //  Getters e setters
    // ------------------------------------------------------------------ //

    public EstadoConversa getEstado() { return estado; }
    public void setEstado(EstadoConversa estado) { this.estado = estado; }

    public boolean isCpfValidado() { return cpfValidado; }
    public void setCpfValidado(boolean v) { this.cpfValidado = v; }

    public String getNomeUsuario() { return nomeUsuario; }
    public void setNomeUsuario(String v) { this.nomeUsuario = v; }

    public String getDescricaoOriginalPedido() { return descricaoOriginalPedido; }
    public void setDescricaoOriginalPedido(String v) { this.descricaoOriginalPedido = v; }

    public Integer getIdProduto() { return idProduto; }
    public void setIdProduto(Integer v) { this.idProduto = v; }

    public String getNomeProduto() { return nomeProduto; }
    public void setNomeProduto(String v) { this.nomeProduto = v; }

    public String getTamanhoProduto() { return tamanhoProduto; }
    public void setTamanhoProduto(String v) { this.tamanhoProduto = v; }

    public String getCorProduto() { return corProduto; }
    public void setCorProduto(String v) { this.corProduto = v; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer v) { this.quantidade = v; }

    public Produto getUltimoProdutoComprado() { return ultimoProdutoComprado; }
    public void setUltimoProdutoComprado(Produto v) { this.ultimoProdutoComprado = v; }

    public int getUltimaQuantidadeComprada() { return ultimaQuantidadeComprada; }
    public void setUltimaQuantidadeComprada(int v) { this.ultimaQuantidadeComprada = v; }

    public Produto getProdutoSelecionado() { return produtoSelecionado; }
    public void setProdutoSelecionado(Produto v) { this.produtoSelecionado = v; }

    public int getQuantidadeDesejada() { return quantidadeDesejada; }
    public void setQuantidadeDesejada(int v) { this.quantidadeDesejada = v; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String v) { this.endereco = v; }

    public String getPagamento() { return pagamento; }
    public void setPagamento(String v) { this.pagamento = v; }

    public String getPagamentoPreferido() { return pagamentoPreferido; }
    public void setPagamentoPreferido(String v) { this.pagamentoPreferido = v; }
}