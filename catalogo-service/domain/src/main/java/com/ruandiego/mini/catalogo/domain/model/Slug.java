package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Trecho da URL que identifica um produto ou categoria, ex.: "esfera-chrome" em /produtos/esfera-chrome.
 * Só minúsculas sem acento, números e hífens entre blocos, até 80 caracteres.
 */
public record Slug(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int TAMANHO_MAXIMO = 80;

    public Slug {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException("slug é obrigatório");
        }
        if (valor.length() > TAMANHO_MAXIMO || !FORMATO.matcher(valor).matches()) {
            throw new RegraDeNegocioException(
                    "slug deve ter até 80 caracteres, só minúsculas sem acento, números e hífens: " + valor);
        }
    }

    /** Gera o slug a partir de um texto livre: "Anel Íris Duplo!" vira "anel-iris-duplo". */
    public static Slug gerarDe(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegraDeNegocioException("não dá para gerar slug de um texto vazio");
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > TAMANHO_MAXIMO) {
            slug = slug.substring(0, TAMANHO_MAXIMO).replaceAll("-+$", "");
        }
        return new Slug(slug);
    }

    @Override
    public String toString() {
        return valor;
    }
}
