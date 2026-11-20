package com.ruandiego.mini.pedido.domain;

/**
 * Uma regra do pedido foi violada (ex.: pedido sem itens, quantidade inválida).
 * O domínio só lança; quem traduz para HTTP 422 é o adapter REST.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
