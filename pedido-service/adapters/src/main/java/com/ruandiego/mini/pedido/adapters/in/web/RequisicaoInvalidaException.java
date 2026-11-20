package com.ruandiego.mini.pedido.adapters.in.web;

/** O pedido HTTP está malformado (ex.: id que não é UUID). O tratador de erros traduz para 400. */
class RequisicaoInvalidaException extends RuntimeException {

    RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
