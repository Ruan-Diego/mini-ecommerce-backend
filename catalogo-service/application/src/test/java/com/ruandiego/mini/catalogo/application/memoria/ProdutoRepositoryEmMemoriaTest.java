package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.contrato.ProdutoRepositoryContrato;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;

/** O adapter em memória passa no mesmo contrato que o JPA. */
class ProdutoRepositoryEmMemoriaTest extends ProdutoRepositoryContrato {

    private final ProdutoRepositoryEmMemoria produtos = new ProdutoRepositoryEmMemoria();
    private final CategoriaRepositoryEmMemoria categorias = new CategoriaRepositoryEmMemoria();

    @Override
    protected ProdutoRepository produtos() {
        return produtos;
    }

    @Override
    protected CategoriaRepository categorias() {
        return categorias;
    }
}
