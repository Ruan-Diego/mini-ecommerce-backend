package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.domain.model.Cupom;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Linha da tabela carrinho (e seus itens). Só dados: as regras ficam no Carrinho do domínio. */
@Entity
@Table(name = "carrinho")
class CarrinhoJpa {

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(name = "cliente_id", nullable = false, length = 64)
    private String clienteId;

    // O cupom é guardado inteiro, em colunas planas: ou todas preenchidas, ou todas nulas.
    @Column(name = "cupom_codigo", length = 40)
    private String cupomCodigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "cupom_tipo", length = 12)
    private Cupom.Tipo cupomTipo;

    @Column(name = "cupom_valor", precision = 12, scale = 2)
    private BigDecimal cupomValor;

    @Column(name = "cupom_valido_ate", columnDefinition = "datetime(6)")
    private Instant cupomValidoAte;

    @Column(name = "cupom_valor_minimo", precision = 12, scale = 2)
    private BigDecimal cupomValorMinimo;

    // EAGER porque o Carrinho do domínio sempre vem com os itens.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_carrinho", joinColumns = @JoinColumn(name = "carrinho_id", columnDefinition = "char(36)"))
    @OrderColumn(name = "posicao")
    private List<ItemCarrinhoJpa> itens = new ArrayList<>();

    protected CarrinhoJpa() {
        // exigido pelo JPA
    }

    CarrinhoJpa(String id, String clienteId, Cupom cupom, List<ItemCarrinhoJpa> itens) {
        this.id = id;
        this.clienteId = clienteId;
        if (cupom != null) {
            this.cupomCodigo = cupom.codigo();
            this.cupomTipo = cupom.tipo();
            this.cupomValor = cupom.valor();
            this.cupomValidoAte = cupom.validoAte();
            this.cupomValorMinimo = cupom.valorMinimo().valor();
        }
        this.itens = new ArrayList<>(itens);
    }

    String getId() {
        return id;
    }

    String getClienteId() {
        return clienteId;
    }

    String getCupomCodigo() {
        return cupomCodigo;
    }

    Cupom.Tipo getCupomTipo() {
        return cupomTipo;
    }

    BigDecimal getCupomValor() {
        return cupomValor;
    }

    Instant getCupomValidoAte() {
        return cupomValidoAte;
    }

    BigDecimal getCupomValorMinimo() {
        return cupomValorMinimo;
    }

    List<ItemCarrinhoJpa> getItens() {
        return itens;
    }
}
