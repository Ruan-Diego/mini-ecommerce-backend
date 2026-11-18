package com.ruandiego.mini.catalogo.domain;

/**
 * Uma regra do catálogo foi violada (ex.: preço zero, SKU mal formado).
 * O domínio só lança; quem traduz para HTTP 422 é o adapter REST.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
