package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Adapter real do port CategoriaRepository. */
@Repository
public class CategoriaRepositoryJpa implements CategoriaRepository {

    private final CategoriaJpaRepository jpa;

    CategoriaRepositoryJpa(CategoriaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Categoria categoria) {
        jpa.save(CatalogoMapper.paraJpa(categoria));
    }

    @Override
    public Optional<Categoria> buscarPorId(CategoriaId id) {
        return jpa.findById(id.toString()).map(CatalogoMapper::paraDominio);
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
        return jpa.findBySlug(slug.valor()).map(CatalogoMapper::paraDominio);
    }

    @Override
    public List<Categoria> listarTodas() {
        return jpa.findAll().stream().map(CatalogoMapper::paraDominio).toList();
    }
}
