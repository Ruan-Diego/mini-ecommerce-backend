package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;

import java.util.Optional;

/** Port de saída do carrinho do cliente logado: durável (MySQL), um por cliente. */
public interface CarrinhoClienteRepository {

    Optional<Carrinho> buscar(ClienteId cliente);

    /** Cria ou substitui o carrinho do cliente (itens e cupom). */
    void salvar(ClienteId cliente, Carrinho carrinho);
}
