package com.ruandiego.mini.pedido.application.port.out;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;

import java.util.Optional;

/**
 * Port de saída para ler o catálogo. O pedido não acessa o banco do catálogo (cada serviço só vê o
 * próprio schema): o adapter real chama a API HTTP e o pedido nunca confia em nome ou preço do navegador.
 */
public interface CatalogoConsulta {

    record Produto(ProdutoId id, String nome, Dinheiro preco) {
    }

    /**
     * Produto ativo, ou vazio se não existe ou está inativo.
     *
     * @throws com.ruandiego.mini.pedido.application.ServicoIndisponivelException se o catálogo não respondeu
     */
    Optional<Produto> buscar(ProdutoId id);
}
