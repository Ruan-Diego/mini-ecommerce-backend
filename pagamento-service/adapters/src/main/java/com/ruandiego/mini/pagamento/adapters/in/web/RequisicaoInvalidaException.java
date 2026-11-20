package com.ruandiego.mini.pagamento.adapters.in.web;

/** O pagamento HTTP está malformado (ex.: id que não é UUID). O tratador de erros traduz para 400. */
class RequisicaoInvalidaException extends RuntimeException {

    RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
