package com.ruandiego.mini.carrinho.application;

/** O recurso pedido não existe (produto, cupom). O adapter REST traduz para 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
