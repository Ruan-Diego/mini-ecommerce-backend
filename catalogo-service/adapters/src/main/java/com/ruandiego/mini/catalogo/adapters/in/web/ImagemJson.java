package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.domain.model.Imagem;

/** Imagem no JSON, usada tanto na resposta quanto na requisição. */
public record ImagemJson(String url, String textoAlternativo) {

    static ImagemJson de(Imagem imagem) {
        return new ImagemJson(imagem.url(), imagem.textoAlternativo());
    }

    /** A validação (URL absoluta, texto alternativo) fica no value object do domínio. */
    Imagem paraDominio() {
        return new Imagem(url, textoAlternativo);
    }
}
