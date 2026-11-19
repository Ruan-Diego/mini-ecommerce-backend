package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;

/** Categoria no JSON. `paiId` nulo = categoria raiz. */
public record CategoriaResposta(String id, String nome, String slug, String paiId) {

    static CategoriaResposta de(Categoria categoria) {
        return new CategoriaResposta(categoria.id().toString(), categoria.nome(), categoria.slug().valor(),
                categoria.paiId().map(CategoriaId::toString).orElse(null));
    }
}
