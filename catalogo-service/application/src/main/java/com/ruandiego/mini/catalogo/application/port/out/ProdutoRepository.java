package com.ruandiego.mini.catalogo.application.port.out;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Ordem;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.Optional;
import java.util.Set;

/**
 * Port de saída: o que os use cases precisam guardar e consultar de produtos.
 * Descrito em termos de negócio; o adapter JPA decide como fazer isso em SQL.
 */
public interface ProdutoRepository {

    /** Insere ou atualiza. */
    void salvar(Produto produto);

    Optional<Produto> buscarPorId(ProdutoId id);

    Optional<Produto> buscarPorSlug(Slug slug);

    boolean existeComSku(Sku sku);

    boolean existeComSlug(Slug slug);

    Pagina<Produto> buscar(Criterios criterios);

    /**
     * Critérios já resolvidos pelo use case (ex.: slug da categoria virou o conjunto de ids com as subcategorias).
     * categorias vazio = qualquer categoria; texto, precoMin e precoMax nulos = sem filtro.
     */
    record Criterios(String texto, Set<CategoriaId> categorias, Dinheiro precoMin, Dinheiro precoMax,
                     boolean somenteAtivos, Ordem ordem, int pagina, int tamanho) {

        public Criterios {
            categorias = Set.copyOf(categorias);
        }
    }
}
