package com.ruandiego.mini.pedido.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Linha da tabela outbox: um evento gravado junto com o pedido, esperando o relay. */
@Entity
@Table(name = "outbox")
class OutboxJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(nullable = false, columnDefinition = "char(36)", updatable = false)
    private String id;

    @Column(nullable = false, length = 100, updatable = false)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "criada_em", nullable = false, columnDefinition = "datetime(6)", updatable = false)
    private Instant criadaEm;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "publicada_em", columnDefinition = "datetime(6)")
    private Instant publicadaEm;

    protected OutboxJpa() {
        // exigido pelo JPA
    }

    OutboxJpa(String id, String tipo, String payload, Instant criadaEm, String traceId) {
        this.traceId = traceId;
        this.id = id;
        this.tipo = tipo;
        this.payload = payload;
        this.criadaEm = criadaEm;
    }

    Long getSeq() {
        return seq;
    }

    String getId() {
        return id;
    }

    String getTipo() {
        return tipo;
    }

    String getPayload() {
        return payload;
    }

    Instant getCriadaEm() {
        return criadaEm;
    }

    String getTraceId() {
        return traceId;
    }

    Instant getPublicadaEm() {
        return publicadaEm;
    }

    void marcarPublicada(Instant quando) {
        this.publicadaEm = quando;
    }
}
