package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.Optional;

/**
 * Port de saída para consultar o catálogo. O carrinho não acessa o banco do catálogo (cada serviço
 * só vê o próprio schema): o adapter real chama a API HTTP do catalogo-service.
 */
public interface CatalogoProdutos {

    /** Dados do produto que o carrinho precisa guardar. */
    record Produto(ProdutoId id, String nome, Dinheiro preco) {
    }

    /**
     * Produto ativo, ou vazio se não existe ou está inativo.
     *
     * @throws com.ruandiego.mini.carrinho.application.ServicoIndisponivelException se o catálogo não respondeu
     */
    Optional<Produto> buscar(ProdutoId id);
}
