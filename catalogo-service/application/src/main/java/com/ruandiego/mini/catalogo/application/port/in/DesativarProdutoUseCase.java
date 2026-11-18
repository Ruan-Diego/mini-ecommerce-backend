package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.domain.model.ProdutoId;

/**
 * Admin: tira o produto da vitrine. Não apaga: pedidos antigos continuam apontando para ele.
 * Desativar um produto já inativo não é erro.
 */
public interface DesativarProdutoUseCase {

    /** @throws com.ruandiego.mini.catalogo.application.NaoEncontradoException se o produto não existir */
    void desativar(ProdutoId id);
}
