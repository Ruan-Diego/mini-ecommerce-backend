package com.ruandiego.mini.catalogo.application;

/**
 * A operação conflita com dados que já existem (SKU ou slug em uso). O adapter REST traduz para 409.
 * É diferente de RegraDeNegocioException: o produto em si é válido, quem impede é outro produto.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
