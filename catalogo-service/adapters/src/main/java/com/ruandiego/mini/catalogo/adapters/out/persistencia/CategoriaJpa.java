package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha da tabela categoria. Só dados: as regras ficam na Categoria do domínio. */
@Entity
@Table(name = "categoria")
class CategoriaJpa {

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(nullable = false, length = 80)
    private String slug;

    /** Só o id do pai, como no domínio: cada categoria é um agregado próprio. */
    @Column(name = "pai_id", columnDefinition = "char(36)")
    private String paiId;

    protected CategoriaJpa() {
        // exigido pelo JPA
    }

    CategoriaJpa(String id, String nome, String slug, String paiId) {
        this.id = id;
        this.nome = nome;
        this.slug = slug;
        this.paiId = paiId;
    }

    String getId() {
        return id;
    }

    String getNome() {
        return nome;
    }

    String getSlug() {
        return slug;
    }

    String getPaiId() {
        return paiId;
    }
}
