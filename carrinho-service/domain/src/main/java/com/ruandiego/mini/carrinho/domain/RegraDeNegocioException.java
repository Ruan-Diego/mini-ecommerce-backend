package com.ruandiego.mini.carrinho.domain;

/**
 * Uma regra do carrinho foi violada (ex.: quantidade fora de 1 a 10, cupom vencido).
 * O domínio só lança; quem traduz para HTTP 422 é o adapter REST.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
