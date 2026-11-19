package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.Dinheiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Cupom de desconto, percentual (até 100) ou de valor fixo, com validade e valor mínimo de subtotal.
 * É um value object: dois cupons com os mesmos dados são o mesmo cupom.
 */
public record Cupom(String codigo, Tipo tipo, BigDecimal valor, Instant validoAte, Dinheiro valorMinimo) {

    public enum Tipo { PERCENTUAL, VALOR_FIXO }

    public Cupom {
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("código do cupom é obrigatório");
        }
        codigo = codigo.trim().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(validoAte, "validoAte");
        valorMinimo = valorMinimo == null ? Dinheiro.ZERO : valorMinimo;
        // Sempre 2 casas, como o Dinheiro: sem isto, 10 e 10.00 seriam cupons "diferentes" no equals do record.
        valor = valor.setScale(2, RoundingMode.HALF_EVEN);
        if (valor.signum() <= 0) {
            throw new RegraDeNegocioException("valor do cupom deve ser maior que zero");
        }
        if (tipo == Tipo.PERCENTUAL && valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RegraDeNegocioException("cupom percentual não pode passar de 100%");
        }
    }

    public static Cupom percentual(String codigo, int percentual, Instant validoAte, Dinheiro valorMinimo) {
        return new Cupom(codigo, Tipo.PERCENTUAL, BigDecimal.valueOf(percentual), validoAte, valorMinimo);
    }

    public static Cupom valorFixo(String codigo, Dinheiro desconto, Instant validoAte, Dinheiro valorMinimo) {
        return new Cupom(codigo, Tipo.VALOR_FIXO, desconto.valor(), validoAte, valorMinimo);
    }

    public boolean vencido(Instant agora) {
        return agora.isAfter(validoAte);
    }

    /** Vale para o subtotal? Não pode estar vencido e o subtotal precisa alcançar o mínimo. */
    public boolean aplicavelA(Dinheiro subtotal, Instant agora) {
        return !vencido(agora) && subtotal.compareTo(valorMinimo) >= 0;
    }

    /** Desconto em reais sobre o subtotal. Nunca passa do subtotal (o carrinho não fica negativo). */
    public Dinheiro descontoSobre(Dinheiro subtotal) {
        Dinheiro bruto = tipo == Tipo.PERCENTUAL
                ? new Dinheiro(subtotal.valor().multiply(valor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN))
                : new Dinheiro(valor);
        return bruto.compareTo(subtotal) > 0 ? subtotal : bruto;
    }
}
