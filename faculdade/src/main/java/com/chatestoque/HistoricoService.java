package com.chatestoque;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistoricoService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Salva na raiz do projeto — o mesmo lugar onde ficam pom.xml e src/.
    // "user.dir" é a propriedade do Java que sempre aponta pro diretório
    // de trabalho do processo, que no IntelliJ é a raiz do projeto.
    private final Path pastaHistoricos =
            Paths.get(System.getProperty("user.dir"), "historicos");

    public HistoricoService() {
        try {
            Files.createDirectories(pastaHistoricos);
            System.out.println("=== HISTORICO: arquivos salvos em -> " + pastaHistoricos.toAbsolutePath() + " ===");
        } catch (IOException e) {
            throw new RuntimeException("Nao foi possivel criar a pasta de historicos: " + e.getMessage());
        }
    }

    public String getCaminhoBase() {
        return pastaHistoricos.toAbsolutePath().toString();
    }

    // ------------------------------------------------------------------ //
    //  Escrita
    // ------------------------------------------------------------------ //

    /**
     * Appenda uma linha no CSV da sessão.
     * Usa append=true — nunca reescreve o arquivo inteiro, só adiciona no fim.
     * Isso é seguro e rápido mesmo com históricos longos.
     */
    public void registrar(String sessionId, String autor, String mensagem) {
        Path arquivo = caminhoArquivo(sessionId);

        // Cria o cabeçalho se o arquivo ainda não existe.
        boolean novo = !Files.exists(arquivo);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(arquivo.toFile(), true), // append=true
                        StandardCharsets.UTF_8))) {

            if (novo) {
                writer.write("timestamp,autor,mensagem");
                writer.newLine();
            }

            // Escapa aspas duplas dentro da mensagem (padrão CSV).
            String mensagemEscapada = mensagem.replace("\"", "\"\"");

            writer.write(LocalDateTime.now().format(FMT)
                    + "," + autor
                    + ",\"" + mensagemEscapada + "\"");
            writer.newLine();

        } catch (IOException e) {
            // Loga mas não quebra o fluxo — o histórico é auxiliar, não crítico.
            System.err.println("Erro ao registrar historico [" + sessionId + "]: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Leitura — usada ao recarregar a sessão após reinício do servidor
    // ------------------------------------------------------------------ //

    /**
     * Lê todas as mensagens do CSV e retorna como lista de Mensagem.
     * Retorna lista vazia se o arquivo não existir.
     */
    public List<SessaoChat.Mensagem> carregar(String sessionId) {
        Path arquivo = caminhoArquivo(sessionId);
        List<SessaoChat.Mensagem> mensagens = new ArrayList<>();

        if (!Files.exists(arquivo)) return mensagens;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(arquivo.toFile()), StandardCharsets.UTF_8))) {

            String linha;
            boolean primeiraLinha = true;

            while ((linha = reader.readLine()) != null) {
                // Pula o cabeçalho
                if (primeiraLinha) { primeiraLinha = false; continue; }
                if (linha.isBlank()) continue;

                // Formato: timestamp,autor,"mensagem"
                // Divide nos dois primeiros vírgulas — a mensagem pode ter vírgulas dentro.
                int primeiraVirgula  = linha.indexOf(',');
                int segundaVirgula   = linha.indexOf(',', primeiraVirgula + 1);

                if (primeiraVirgula < 0 || segundaVirgula < 0) continue;

                String autor    = linha.substring(primeiraVirgula + 1, segundaVirgula).trim();
                String mensagem = linha.substring(segundaVirgula + 1).trim();

                // Remove as aspas externas e desfaz o escape de aspas duplas.
                if (mensagem.startsWith("\"") && mensagem.endsWith("\"")) {
                    mensagem = mensagem.substring(1, mensagem.length() - 1);
                }
                mensagem = mensagem.replace("\"\"", "\"");

                mensagens.add(new SessaoChat.Mensagem(autor, mensagem));
            }

        } catch (IOException e) {
            System.err.println("Erro ao carregar historico [" + sessionId + "]: " + e.getMessage());
        }

        return mensagens;
    }

    // ------------------------------------------------------------------ //
    //  Exclusão — chamada ao encerrar o atendimento
    // ------------------------------------------------------------------ //

    /**
     * Deleta o arquivo CSV da sessão.
     * Chamado quando o usuário digita "sair" ou a sessão expira.
     */
    public void deletar(String sessionId) {
        try {
            Files.deleteIfExists(caminhoArquivo(sessionId));
        } catch (IOException e) {
            System.err.println("Erro ao deletar historico [" + sessionId + "]: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper
    // ------------------------------------------------------------------ //

    private Path caminhoArquivo(String sessionId) {
        // Sanitiza o sessionId pra evitar path traversal (ex: "../../etc/passwd")
        String idSanitizado = sessionId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return pastaHistoricos.resolve("historico_" + idSanitizado + ".csv");
    }
}