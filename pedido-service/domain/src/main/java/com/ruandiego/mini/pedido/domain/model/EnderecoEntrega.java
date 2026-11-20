package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;

/** Endereço de entrega, congelado no pedido. CEP guardado só com os 8 dígitos. */
public record EnderecoEntrega(String destinatario, String cep, String logradouro, String numero,
                              String complemento, String bairro, String cidade, String uf) {

    public EnderecoEntrega {
        destinatario = obrigatorio(destinatario, "destinatário");
        cep = cep == null ? "" : cep.replaceAll("[^0-9]", "");
        if (cep.length() != 8) {
            throw new RegraDeNegocioException("CEP deve ter 8 dígitos");
        }
        logradouro = obrigatorio(logradouro, "logradouro");
        numero = obrigatorio(numero, "número");
        complemento = complemento == null || complemento.isBlank() ? null : complemento.trim();
        bairro = obrigatorio(bairro, "bairro");
        cidade = obrigatorio(cidade, "cidade");
        uf = obrigatorio(uf, "UF").toUpperCase();
        if (uf.length() != 2) {
            throw new RegraDeNegocioException("UF deve ter 2 letras");
        }
    }

    private static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException(campo + " é obrigatório");
        }
        return valor.trim();
    }
}
