package com.ruandiego.mini.catalogo.application;

/**
 * A operação conflita com dados que já existem (SKU ou slug em uso). O adapter REST traduz para 409.
 * É diferente de RegraDeNegocioException: o produto em si é válido, quem impede é outro produto.
 * `campo` diz qual campo do pedido causou o conflito ("sku", "slug"), para a tela pôr o erro no lugar certo.
 */
public class ConflitoException extends RuntimeException {

    private final String campo;

    public ConflitoException(String mensagem) {
        this(null, mensagem);
    }

    public ConflitoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    /** O campo em conflito, ou nulo quando o conflito não é de um campo só. */
    public String campo() {
        return campo;
    }
}
