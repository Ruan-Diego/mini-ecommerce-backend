package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ListarCategoriasUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ObterProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Slug;

import java.text.Collator;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Casos de uso da vitrine (só leitura). Só produtos ativos aparecem. */
public class ConsultaCatalogoService implements BuscarProdutosUseCase, ObterProdutoUseCase, ListarCategoriasUseCase {

    private static final Collator ORDEM_ALFABETICA = Collator.getInstance(Locale.of("pt", "BR"));

    private final ProdutoRepository produtos;
    private final CategoriaRepository categorias;

    public ConsultaCatalogoService(ProdutoRepository produtos, CategoriaRepository categorias) {
        this.produtos = Objects.requireNonNull(produtos, "produtos");
        this.categorias = Objects.requireNonNull(categorias, "categorias");
    }

    @Override
    public Pagina<Produto> buscar(Filtro filtro) {
        Set<CategoriaId> idsCategoria = Set.of();
        if (filtro.categoria() != null) {
            Optional<Categoria> categoria = categorias.buscarPorSlug(filtro.categoria());
            if (categoria.isEmpty()) {
                // Categoria inexistente na URL: vitrine vazia, não erro.
                return new Pagina<>(List.of(), filtro.pagina(), filtro.tamanho(), 0);
            }
            idsCategoria = comSubcategorias(categoria.get().id());
        }
        return produtos.buscar(new ProdutoRepository.Criterios(filtro.texto(), idsCategoria, filtro.precoMin(),
                filtro.precoMax(), true, filtro.ordem(), filtro.pagina(), filtro.tamanho()));
    }

    @Override
    public Produto obterPorSlug(Slug slug) {
        return produtos.buscarPorSlug(slug)
                .filter(Produto::visivelNaVitrine)
                .orElseThrow(() -> new NaoEncontradoException("produto não encontrado: " + slug));
    }

    @Override
    public Produto obterPorId(ProdutoId id) {
        return produtos.buscarPorId(id)
                .filter(Produto::visivelNaVitrine)
                .orElseThrow(() -> new NaoEncontradoException("produto não encontrado: " + id));
    }

    @Override
    public List<Categoria> listar() {
        return categorias.listarTodas().stream()
                .sorted(Comparator.comparing(Categoria::nome, ORDEM_ALFABETICA))
                .toList();
    }

    /** A categoria e todas as descendentes: filtrar por "Objetos" também traz "Anéis" e "Blocos". */
    private Set<CategoriaId> comSubcategorias(CategoriaId raiz) {
        Map<CategoriaId, List<CategoriaId>> filhosPorPai = categorias.listarTodas().stream()
                .filter(c -> c.paiId().isPresent())
                .collect(Collectors.groupingBy(c -> c.paiId().get(),
                        Collectors.mapping(Categoria::id, Collectors.toList())));

        Set<CategoriaId> resultado = new HashSet<>();
        Deque<CategoriaId> pendentes = new ArrayDeque<>(List.of(raiz));
        while (!pendentes.isEmpty()) {
            CategoriaId atual = pendentes.pop();
            // add devolve false se já visitada: protege contra ciclo nos dados.
            if (resultado.add(atual)) {
                pendentes.addAll(filhosPorPai.getOrDefault(atual, List.of()));
            }
        }
        return resultado;
    }
}
