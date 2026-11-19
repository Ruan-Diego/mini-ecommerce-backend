package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.Slug;

/** Detalhe do produto na vitrine. Produto inativo conta como não encontrado. */
public interface ObterProdutoUseCase {

    /** @throws com.ruandiego.mini.catalogo.application.NaoEncontradoException se não existe ou está inativo */
    Produto obterPorSlug(Slug slug);
}
