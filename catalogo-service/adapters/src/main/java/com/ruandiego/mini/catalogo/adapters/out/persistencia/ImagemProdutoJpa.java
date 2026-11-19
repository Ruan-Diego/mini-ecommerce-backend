package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Linha da tabela imagem_produto. É @Embeddable porque não tem identidade própria: pertence ao produto. */
@Embeddable
class ImagemProdutoJpa {

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "texto_alternativo", nullable = false, length = 200)
    private String textoAlternativo;

    protected ImagemProdutoJpa() {
        // exigido pelo JPA
    }

    ImagemProdutoJpa(String url, String textoAlternativo) {
        this.url = url;
        this.textoAlternativo = textoAlternativo;
    }

    String getUrl() {
        return url;
    }

    String getTextoAlternativo() {
        return textoAlternativo;
    }
}
