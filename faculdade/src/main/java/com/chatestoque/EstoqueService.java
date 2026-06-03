package com.chatestoque;

import java.util.ArrayList;
import java.util.List;

// Cuida de tudo relacionado ao estoque: guardar, listar e buscar produtos.
// Os dados ficam em memória uma lista — não tem banco de dados aqui.
public class EstoqueService {

    // O "static" aqui é importante: significa que essa lista é compartilhada
    // entre todas as instâncias de EstoqueService.
    // Sem o static, cada vez que o ChatController criasse um "new EstoqueService()"
    // o estoque estaria zerado. Com static, a lista persiste enquanto o servidor tiver rodando.
    private static final List<Produto> produtos = new ArrayList<>();

    // Esse bloco roda uma única vez quando a aplicação sobe.
    // É o estoque inicial — os produtos que já existem antes de qualquer usuário interagir.
    static {
        produtos.add(new Produto(1, "camisa", "P",  "rosa",   8));
        produtos.add(new Produto(2, "camisa", "M",  "rosa",   10));
        produtos.add(new Produto(3, "camisa", "G",  "rosa",   12));
        produtos.add(new Produto(4, "camisa", "GG", "preta",  5));
        produtos.add(new Produto(5, "camisa", "GG", "branca", 3));
    }

    // Retorna a lista inteira de produtos.
    // Atenção: retorna a lista de verdade, não uma cópia.
    // Isso significa que quem receber pode adicionar ou remover itens diretamente —
    // e é exatamente isso que o ChatController faz ao cadastrar um produto novo.
    public List<Produto> listar() {
        return produtos;
    }

    // Busca um produto pela combinação exata de nome + tamanho + cor.
    // Precisa das três informações porque tem vários produtos com o mesmo nome
    // mas atributos diferentes (ex: camisa G rosa vs camisa GG preta).
    // Retorna null se não encontrar nada com essa combinação.
    public Produto buscar(String nome, String tamanho, String cor) {

        for (Produto p : produtos) {
            // ignoreCase porque o usuário pode digitar "Rosa", "ROSA" ou "rosa" —
            // todos precisam encontrar o mesmo produto.
            if (p.getNome().equalsIgnoreCase(nome)
                    && p.getTamanho().equalsIgnoreCase(tamanho)
                    && p.getCor().equalsIgnoreCase(cor)) {
                return p;
            }
        }

        return null;
    }

    }
