package com.ruandiego.mini.pagamento.application;

/** O gateway de pagamento não respondeu. O adapter REST traduz para 503. Nada é gravado quando isso acontece. */
public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
