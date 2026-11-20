package com.ruandiego.mini.estoque.adapters.out.persistencia;

import com.ruandiego.mini.estoque.domain.model.StatusReserva;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Linha de reserva, com os itens em item_reserva. Entidade JPA, separada da Reserva do domínio. */
@Entity
@Table(name = "reserva")
class ReservaJpa {

    @Embeddable
    static class ItemJpa {

        @Column(name = "produto_id", columnDefinition = "char(36)", nullable = false)
        private String produtoId;

        @Column(nullable = false)
        private int quantidade;

        protected ItemJpa() {
            // exigido pelo JPA
        }

        ItemJpa(String produtoId, int quantidade) {
            this.produtoId = produtoId;
            this.quantidade = quantidade;
        }

        String getProdutoId() {
            return produtoId;
        }

        int getQuantidade() {
            return quantidade;
        }
    }

    @Id
    @Column(name = "pedido_id", columnDefinition = "char(36)", nullable = false, updatable = false)
    private String pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusReserva status;

    @Column(name = "criada_em", nullable = false, columnDefinition = "datetime(6)", updatable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false, columnDefinition = "datetime(6)", updatable = false)
    private Instant expiraEm;

    @Column(name = "atualizada_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant atualizadaEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_reserva", joinColumns = @JoinColumn(name = "pedido_id"))
    private List<ItemJpa> itens = new ArrayList<>();

    protected ReservaJpa() {
        // exigido pelo JPA
    }

    ReservaJpa(String pedidoId, StatusReserva status, Instant criadaEm, Instant expiraEm, Instant atualizadaEm,
               List<ItemJpa> itens) {
        this.pedidoId = pedidoId;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
        this.itens = new ArrayList<>(itens);
        atualizar(status, atualizadaEm);
    }

    /** Os itens não mudam depois de criada: só o status e a data de atualização. */
    void atualizar(StatusReserva status, Instant atualizadaEm) {
        this.status = status;
        this.atualizadaEm = atualizadaEm;
    }

    String getPedidoId() {
        return pedidoId;
    }

    StatusReserva getStatus() {
        return status;
    }

    Instant getCriadaEm() {
        return criadaEm;
    }

    Instant getExpiraEm() {
        return expiraEm;
    }

    Instant getAtualizadaEm() {
        return atualizadaEm;
    }

    List<ItemJpa> getItens() {
        return itens;
    }
}
