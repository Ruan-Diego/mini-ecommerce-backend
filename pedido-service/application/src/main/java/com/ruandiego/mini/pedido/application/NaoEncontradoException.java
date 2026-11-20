package com.ruandiego.mini.pedido.application;

/** O recurso pedido não existe (pedido, produto). O adapter REST traduz para 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
