package com.ruandiego.mini.pedido.application;

/** Um serviço de que o pedido depende (catálogo) não respondeu. O adapter REST traduz para 503. */
public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
