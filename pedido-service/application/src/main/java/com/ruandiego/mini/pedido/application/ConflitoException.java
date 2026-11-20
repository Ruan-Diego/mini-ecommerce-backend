package com.ruandiego.mini.pedido.application;

/**
 * A operação conflita com o estado atual de outro recurso (ex.: o preço do produto mudou desde que o
 * cliente o viu). O pedido em si é válido; o cliente precisa rever os dados. O adapter REST traduz para 409.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
