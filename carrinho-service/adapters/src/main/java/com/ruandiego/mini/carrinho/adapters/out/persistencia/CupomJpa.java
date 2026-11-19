package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.domain.model.Cupom;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Linha da tabela cupom (somente leitura para a aplicação). */
@Entity
@Table(name = "cupom")
class CupomJpa {

    @Id
    @Column(length = 40)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Cupom.Tipo tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "valido_ate", nullable = false, columnDefinition = "datetime(6)")
    private Instant validoAte;

    @Column(name = "valor_minimo", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorMinimo;

    protected CupomJpa() {
        // exigido pelo JPA
    }

    String getCodigo() {
        return codigo;
    }

    Cupom.Tipo getTipo() {
        return tipo;
    }

    BigDecimal getValor() {
        return valor;
    }

    Instant getValidoAte() {
        return validoAte;
    }

    BigDecimal getValorMinimo() {
        return valorMinimo;
    }
}
