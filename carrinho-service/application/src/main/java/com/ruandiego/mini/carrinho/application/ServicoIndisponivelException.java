package com.ruandiego.mini.carrinho.application;

/** Um serviço de que o carrinho depende (catálogo) não respondeu. O adapter REST traduz para 503. */
public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
