package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;

/**
 * Converte entre domínio e JPA. Na volta usa reconstituir(): as validações do domínio rodam de novo,
 * então um dado inválido gravado por fora (SQL manual) aparece como erro, em vez de virar um Produto quebrado.
 */
final class CatalogoMapper {

    private CatalogoMapper() {
    }

    static ProdutoJpa paraJpa(Produto p) {
        return new ProdutoJpa(p.id().toString(), p.sku().valor(), p.nome(), p.slug().valor(), p.descricao(),
                p.preco().valor(), p.categoriaId().toString(), p.ativo(),
                p.imagens().stream().map(i -> new ImagemProdutoJpa(i.url(), i.textoAlternativo())).toList());
    }

    static Produto paraDominio(ProdutoJpa j) {
        return Produto.reconstituir(ProdutoId.de(j.getId()), new Sku(j.getSku()), j.getNome(), new Slug(j.getSlug()),
                j.getDescricao(), new Dinheiro(j.getPreco()),
                j.getImagens().stream().map(i -> new Imagem(i.getUrl(), i.getTextoAlternativo())).toList(),
                CategoriaId.de(j.getCategoriaId()), j.isAtivo());
    }

    static CategoriaJpa paraJpa(Categoria c) {
        return new CategoriaJpa(c.id().toString(), c.nome(), c.slug().valor(),
                c.paiId().map(CategoriaId::toString).orElse(null));
    }

    static Categoria paraDominio(CategoriaJpa j) {
        return Categoria.reconstituir(CategoriaId.de(j.getId()), j.getNome(), new Slug(j.getSlug()),
                j.getPaiId() == null ? null : CategoriaId.de(j.getPaiId()));
    }
}
