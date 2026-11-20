package com.ruandiego.mini.estoque.domain;

/**
 * Uma regra do estoque foi violada (ex.: valor zero, cartão com número inválido).
 * O domínio só lança; quem traduz para HTTP 422 é o adapter REST.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
