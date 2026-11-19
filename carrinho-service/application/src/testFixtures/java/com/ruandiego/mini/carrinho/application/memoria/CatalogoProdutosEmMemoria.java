package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CatalogoProdutos, para testes: só conhece o que for cadastrado com {@code cadastrar}. */
public class CatalogoProdutosEmMemoria implements CatalogoProdutos {

    private final Map<ProdutoId, Produto> dados = new HashMap<>();

    public void cadastrar(Produto produto) {
        dados.put(produto.id(), produto);
    }

    public void limpar() {
        dados.clear();
    }

    @Override
    public Optional<Produto> buscar(ProdutoId id) {
        return Optional.ofNullable(dados.get(id));
    }
}
