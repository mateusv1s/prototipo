package com.chatestoque;

import org.springframework.core.io.ClassPathResource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Carrega as mensagens do sistema a partir de um arquivo CSV (respostas.csv).
// A ideia é centralizar os textos fora do código — assim dá pra editar as mensagens
// sem precisar recompilar nada.
// Na prática, o ChatController ainda tem várias mensagens hardcoded e usa pouco esse serviço,
// mas a estrutura está aqui pra ser expandida.
public class RespostaService {

    // Dicionário chave → mensagem. Ex: "boas_vindas" → "Fala parceiro! Me manda seu CPF."
    private final Map<String, String> respostas = new HashMap<>();

    // Assim que o objeto é criado, já carrega o CSV.
    // Se o arquivo não existir ou tiver erro, a aplicação não sobe — melhor do que subir quebrada.
    public RespostaService() {
        carregarCSV();
    }

    private void carregarCSV() {
        try {
            // Procura o arquivo dentro da pasta resources/ do projeto.
            ClassPathResource resource = new ClassPathResource("respostas.csv");

            // Abre o arquivo garantindo leitura em UTF-8 — sem isso, acentos viravam lixo.
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            String linha;
            boolean primeiraLinha = true;

            while ((linha = br.readLine()) != null) {

                // A primeira linha é o cabeçalho (chave,mensagem) — pula ela.
                if (primeiraLinha) {
                    primeiraLinha = false;
                    continue;
                }

                // Divide pelo primeiro vírgula em no máximo 2 partes.
                // O limite de 2 é necessário porque a mensagem pode ter vírgulas dentro —
                // sem esse limite, uma mensagem "Olá, tudo bem?" quebraria em 3 partes.
                String[] partes = linha.split(",", 2);

                // Linha mal formada (sem vírgula, por exemplo) — ignora e continua.
                if (partes.length < 2) continue;

                String chave = partes[0].trim();
                String mensagem = partes[1].trim();

                // Remove as aspas duplas que envolvem a mensagem no CSV.
                // Ex: "\"Fala parceiro!\"" vira "Fala parceiro!"
                if (mensagem.startsWith("\"") && mensagem.endsWith("\"")) {
                    mensagem = mensagem.substring(1, mensagem.length() - 1);
                }

                // Transforma o marcador literal \n em quebra de linha de verdade.
                // No CSV fica escrito "linha1\nlinha2", e aqui vira uma quebra real.
                mensagem = mensagem.replace("\\n", "\n");

                respostas.put(chave, mensagem);
            }

            br.close();

        } catch (Exception e) {
            // Qualquer problema com o arquivo vira um erro que para a aplicação na inicialização.
            throw new RuntimeException("Erro ao carregar respostas.csv: " + e.getMessage());
        }
    }

    // Busca uma mensagem pela chave. Se a chave não existir, avisa no retorno
    // em vez de retornar null — ajuda a encontrar chaves erradas durante o desenvolvimento.
    public String get(String chave) {
        return respostas.getOrDefault(chave, "Resposta não encontrada para: " + chave);
    }
}