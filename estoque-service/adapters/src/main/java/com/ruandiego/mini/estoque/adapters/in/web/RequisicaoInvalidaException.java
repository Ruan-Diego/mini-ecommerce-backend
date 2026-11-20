package com.ruandiego.mini.estoque.adapters.in.web;

/** A requisição HTTP está malformada (ex.: id que não é UUID). O tratador de erros traduz para 400. */
class RequisicaoInvalidaException extends RuntimeException {

    RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
