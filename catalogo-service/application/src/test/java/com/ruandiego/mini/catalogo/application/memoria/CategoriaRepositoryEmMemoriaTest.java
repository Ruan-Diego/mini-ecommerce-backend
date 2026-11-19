package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.contrato.CategoriaRepositoryContrato;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;

/** O adapter em memória passa no mesmo contrato que o JPA. */
class CategoriaRepositoryEmMemoriaTest extends CategoriaRepositoryContrato {

    private final CategoriaRepositoryEmMemoria categorias = new CategoriaRepositoryEmMemoria();

    @Override
    protected CategoriaRepository categorias() {
        return categorias;
    }
}
