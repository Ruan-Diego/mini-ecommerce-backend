package com.ruandiego.mini.catalogo.application;

/** O recurso pedido não existe (ou não está visível). O adapter REST traduz para 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
