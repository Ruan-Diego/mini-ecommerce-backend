package com.ruandiego.mini.pagamento.application;

/** O recurso pedido não existe (pagamento, transação). O adapter REST traduz para 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
