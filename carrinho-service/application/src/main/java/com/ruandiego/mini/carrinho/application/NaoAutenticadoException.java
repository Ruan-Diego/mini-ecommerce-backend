package com.ruandiego.mini.carrinho.application;

/** A operação exige cliente logado (ex.: checkout). O adapter REST traduz para 401. */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
