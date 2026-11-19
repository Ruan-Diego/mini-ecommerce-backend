package com.ruandiego.mini.carrinho.adapters.in.web;

/**
 * Entrada que nem chega a ser um valor de domínio (produtoId que não é UUID, header de identificação
 * ausente ou malformado). Vira 400, diferente da regra de negócio violada, que vira 422.
 */
class RequisicaoInvalidaException extends RuntimeException {

    RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
