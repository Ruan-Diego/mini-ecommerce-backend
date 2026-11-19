package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Linha da tabela produto (e suas imagens). Só dados: as regras ficam no Produto do domínio. */
@Entity
@Table(name = "produto")
class ProdutoJpa {

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(nullable = false, length = 32)
    private String sku;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /** Só o id: Categoria é outro agregado, então não há @ManyToOne navegável. */
    @Column(name = "categoria_id", nullable = false, columnDefinition = "char(36)")
    private String categoriaId;

    @Column(nullable = false)
    private boolean ativo;

    // EAGER porque o Produto do domínio sempre vem com as imagens (são no máximo 10).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "imagem_produto", joinColumns = @JoinColumn(name = "produto_id", columnDefinition = "char(36)"))
    @OrderColumn(name = "posicao")
    private List<ImagemProdutoJpa> imagens = new ArrayList<>();

    protected ProdutoJpa() {
        // exigido pelo JPA
    }

    ProdutoJpa(String id, String sku, String nome, String slug, String descricao, BigDecimal preco,
               String categoriaId, boolean ativo, List<ImagemProdutoJpa> imagens) {
        this.id = id;
        this.sku = sku;
        this.nome = nome;
        this.slug = slug;
        this.descricao = descricao;
        this.preco = preco;
        this.categoriaId = categoriaId;
        this.ativo = ativo;
        this.imagens = new ArrayList<>(imagens);
    }

    String getId() {
        return id;
    }

    String getSku() {
        return sku;
    }

    String getNome() {
        return nome;
    }

    String getSlug() {
        return slug;
    }

    String getDescricao() {
        return descricao;
    }

    BigDecimal getPreco() {
        return preco;
    }

    String getCategoriaId() {
        return categoriaId;
    }

    boolean isAtivo() {
        return ativo;
    }

    List<ImagemProdutoJpa> getImagens() {
        return imagens;
    }
}
