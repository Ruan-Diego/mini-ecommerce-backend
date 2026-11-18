package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.domain.model.Categoria;

import java.util.List;

/** Categorias para o menu e os filtros da vitrine, em ordem alfabética. */
public interface ListarCategoriasUseCase {

    List<Categoria> listar();
}
