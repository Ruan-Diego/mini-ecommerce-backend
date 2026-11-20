package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Linha da tabela pagamento. Só dados: a máquina de estados fica no Pagamento do domínio. */
@Entity
@Table(name = "pagamento")
class PagamentoJpa {

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(name = "pedido_id", nullable = false, columnDefinition = "char(36)")
    private String pedidoId;

    @Column(name = "cliente_id", nullable = false, length = 64)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MetodoPagamento metodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPagamento status;

    @Column(name = "transacao_id", length = 80)
    private String transacaoId;

    @Column(name = "pix_codigo", columnDefinition = "text")
    private String pixCodigo;

    @Column(name = "pix_expira_em", columnDefinition = "datetime(6)")
    private Instant pixExpiraEm;

    @Column(name = "motivo_recusa", length = 200)
    private String motivoRecusa;

    @Column(name = "criado_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant atualizadoEm;

    protected PagamentoJpa() {
        // exigido pelo JPA
    }

    PagamentoJpa(String id) {
        this.id = id;
    }

    /** Copia os campos de um pagamento do domínio para esta linha (nova ou já existente). */
    void preencher(String pedidoId, String clienteId, MetodoPagamento metodo, BigDecimal valor, StatusPagamento status,
                   String transacaoId, String pixCodigo, Instant pixExpiraEm, String motivoRecusa,
                   Instant criadoEm, Instant atualizadoEm) {
        this.pedidoId = pedidoId;
        this.clienteId = clienteId;
        this.metodo = metodo;
        this.valor = valor;
        this.status = status;
        this.transacaoId = transacaoId;
        this.pixCodigo = pixCodigo;
        this.pixExpiraEm = pixExpiraEm;
        this.motivoRecusa = motivoRecusa;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    String getId() {
        return id;
    }

    String getPedidoId() {
        return pedidoId;
    }

    String getClienteId() {
        return clienteId;
    }

    MetodoPagamento getMetodo() {
        return metodo;
    }

    BigDecimal getValor() {
        return valor;
    }

    StatusPagamento getStatus() {
        return status;
    }

    String getTransacaoId() {
        return transacaoId;
    }

    String getPixCodigo() {
        return pixCodigo;
    }

    Instant getPixExpiraEm() {
        return pixExpiraEm;
    }

    String getMotivoRecusa() {
        return motivoRecusa;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
