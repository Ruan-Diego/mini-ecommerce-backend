package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Linha da tabela preco_processado: o instante do último PrecoAlterado aplicado de cada produto. */
@Entity
@Table(name = "preco_processado")
class PrecoProcessadoJpa {

    @Id
    @Column(name = "produto_id", columnDefinition = "char(36)")
    private String produtoId;

    @Column(name = "ocorrido_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant ocorridoEm;

    protected PrecoProcessadoJpa() {
        // exigido pelo JPA
    }

    PrecoProcessadoJpa(String produtoId, Instant ocorridoEm) {
        this.produtoId = produtoId;
        this.ocorridoEm = ocorridoEm;
    }

    Instant getOcorridoEm() {
        return ocorridoEm;
    }

    void setOcorridoEm(Instant ocorridoEm) {
        this.ocorridoEm = ocorridoEm;
    }
}
