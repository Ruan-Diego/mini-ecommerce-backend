package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Imagem do produto. O texto alternativo é obrigatório: a vitrine precisa dele para leitor de tela
 * (regra de acessibilidade do projeto).
 */
public record Imagem(String url, String textoAlternativo) {

    public Imagem {
        if (url == null || url.isBlank()) {
            throw new RegraDeNegocioException("URL da imagem é obrigatória");
        }
        url = url.trim();
        if (!ehHttp(url)) {
            throw new RegraDeNegocioException("URL da imagem deve ser http(s) absoluta: " + url);
        }
        if (textoAlternativo == null || textoAlternativo.isBlank()) {
            throw new RegraDeNegocioException("imagem precisa de texto alternativo: " + url);
        }
        textoAlternativo = textoAlternativo.trim();
    }

    private static boolean ehHttp(String url) {
        try {
            URI uri = new URI(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
