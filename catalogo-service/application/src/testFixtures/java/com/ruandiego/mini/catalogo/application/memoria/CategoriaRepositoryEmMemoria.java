package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Slug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CategoriaRepository, para testes. Guarda cópias, como um banco. */
public class CategoriaRepositoryEmMemoria implements CategoriaRepository {

    private final Map<CategoriaId, Categoria> dados = new LinkedHashMap<>();

    /** Para testes que reaproveitam a mesma instância (ex.: bean num contexto Spring de teste). */
    public void limpar() {
        dados.clear();
    }

    @Override
    public void salvar(Categoria categoria) {
        dados.put(categoria.id(), copiar(categoria));
    }

    @Override
    public Optional<Categoria> buscarPorId(CategoriaId id) {
        return Optional.ofNullable(dados.get(id)).map(CategoriaRepositoryEmMemoria::copiar);
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
        return dados.values().stream().filter(c -> c.slug().equals(slug)).findFirst()
                .map(CategoriaRepositoryEmMemoria::copiar);
    }

    @Override
    public List<Categoria> listarTodas() {
        return dados.values().stream().map(CategoriaRepositoryEmMemoria::copiar).toList();
    }

    private static Categoria copiar(Categoria c) {
        return Categoria.reconstituir(c.id(), c.nome(), c.slug(), c.paiId().orElse(null));
    }
}
