package com.ruandiego.mini.catalogo.application.port.out;

import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Slug;

import java.util.List;
import java.util.Optional;

/** Port de saída das categorias. */
public interface CategoriaRepository {

    void salvar(Categoria categoria);

    Optional<Categoria> buscarPorId(CategoriaId id);

    Optional<Categoria> buscarPorSlug(Slug slug);

    /** Todas as categorias. São poucas (dezenas): a árvore é montada em memória pelo use case. */
    List<Categoria> listarTodas();
}
