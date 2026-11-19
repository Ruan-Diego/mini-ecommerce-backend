package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;

import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

import java.util.List;
import java.util.Optional;

/** Port de saída do carrinho do cliente logado: durável (MySQL), um por cliente. */
public interface CarrinhoClienteRepository {

    Optional<Carrinho> buscar(ClienteId cliente);

    /** Cria ou substitui o carrinho do cliente (itens e cupom). */
    void salvar(ClienteId cliente, Carrinho carrinho);

    /** Clientes cujo carrinho tem o produto (para aplicar mudança de preço). */
    List<ClienteId> clientesComProduto(ProdutoId produto);
}
