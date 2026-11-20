package com.ruandiego.mini.estoque.application;

/**
 * A operação conflita com o estado atual (ex.: o pedido já tem um pagamento aprovado ou em andamento).
 * O pagamento em si é válido; quem impede é outro estoque. O adapter REST traduz para 409.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
