package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código de estoque do produto, ex.: "ESF-CHROME-01".
 * Normalizado para maiúsculas; só letras, números e hífens entre blocos, de 3 a 32 caracteres.
 */
public record Sku(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^[A-Z0-9]+(-[A-Z0-9]+)*$");

    public Sku {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException("SKU é obrigatório");
        }
        valor = valor.trim().toUpperCase(Locale.ROOT);
        if (valor.length() < 3 || valor.length() > 32) {
            throw new RegraDeNegocioException("SKU deve ter de 3 a 32 caracteres: " + valor);
        }
        if (!FORMATO.matcher(valor).matches()) {
            throw new RegraDeNegocioException("SKU só aceita letras, números e hífens entre blocos: " + valor);
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
