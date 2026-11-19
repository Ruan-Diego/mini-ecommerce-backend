package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapter real do port ProdutoRepository. Traduz os Criterios de negócio para uma consulta SQL
 * (via Specification do Spring Data) e as linhas de volta para Produto do domínio.
 */
@Repository
public class ProdutoRepositoryJpa implements ProdutoRepository {

    private final ProdutoJpaRepository jpa;

    ProdutoRepositoryJpa(ProdutoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Produto produto) {
        jpa.save(CatalogoMapper.paraJpa(produto));
    }

    @Override
    public Optional<Produto> buscarPorId(ProdutoId id) {
        return jpa.findById(id.toString()).map(CatalogoMapper::paraDominio);
    }

    @Override
    public Optional<Produto> buscarPorSlug(Slug slug) {
        return jpa.findBySlug(slug.valor()).map(CatalogoMapper::paraDominio);
    }

    @Override
    public boolean existeComSku(Sku sku) {
        return jpa.existsBySku(sku.valor());
    }

    @Override
    public boolean existeComSlug(Slug slug) {
        return jpa.existsBySlug(slug.valor());
    }

    @Override
    public Pagina<Produto> buscar(Criterios c) {
        Page<ProdutoJpa> pagina = jpa.findAll(filtro(c), PageRequest.of(c.pagina(), c.tamanho(), ordenacao(c)));
        return new Pagina<>(pagina.getContent().stream().map(CatalogoMapper::paraDominio).toList(),
                c.pagina(), c.tamanho(), pagina.getTotalElements());
    }

    /**
     * Cada critério preenchido vira um AND. A busca por texto usa LIKE: a collation da tabela
     * (utf8mb4_unicode_ci) já ignora maiúsculas e acentos, igual ao adapter em memória.
     */
    private static Specification<ProdutoJpa> filtro(Criterios c) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (c.somenteAtivos()) {
                condicoes.add(cb.isTrue(raiz.get("ativo")));
            }
            if (!c.categorias().isEmpty()) {
                condicoes.add(raiz.get("categoriaId").in(c.categorias().stream().map(CategoriaId::toString).toList()));
            }
            if (c.precoMin() != null) {
                condicoes.add(cb.greaterThanOrEqualTo(raiz.get("preco"), c.precoMin().valor()));
            }
            if (c.precoMax() != null) {
                condicoes.add(cb.lessThanOrEqualTo(raiz.get("preco"), c.precoMax().valor()));
            }
            if (c.texto() != null) {
                String padrao = "%" + escaparLike(c.texto()) + "%";
                condicoes.add(cb.or(
                        cb.like(raiz.get("nome"), padrao, '!'),
                        cb.like(raiz.get("descricao"), padrao, '!'),
                        cb.like(raiz.get("sku"), padrao, '!')));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    /**
     * Sem isso, buscar "100%" ou "a_b" usaria % e _ como curingas do LIKE.
     * O escape é ! e não \, porque no MySQL a barra também escapa aspas dentro do SQL.
     */
    private static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** O id no fim desempata: sem ele, produtos de mesmo nome e preço poderiam trocar de página entre consultas. */
    private static Sort ordenacao(Criterios c) {
        Sort porNome = Sort.by("nome").and(Sort.by("id"));
        return switch (c.ordem()) {
            case NOME -> porNome;
            case MENOR_PRECO -> Sort.by(Sort.Direction.ASC, "preco").and(porNome);
            case MAIOR_PRECO -> Sort.by(Sort.Direction.DESC, "preco").and(porNome);
        };
    }
}
