package com.ruandiego.mini.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor monetário em reais, sempre com 2 casas decimais. */
public record Dinheiro(BigDecimal valor) implements Comparable<Dinheiro> {

    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

    public Dinheiro {
        Objects.requireNonNull(valor, "valor");
        valor = valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static Dinheiro de(String valor) {
        return new Dinheiro(new BigDecimal(valor));
    }

    public Dinheiro somar(Dinheiro outro) {
        return new Dinheiro(valor.add(outro.valor));
    }

    public Dinheiro subtrair(Dinheiro outro) {
        return new Dinheiro(valor.subtract(outro.valor));
    }

    public Dinheiro multiplicar(int quantidade) {
        return new Dinheiro(valor.multiply(BigDecimal.valueOf(quantidade)));
    }

    public boolean ehPositivo() {
        return valor.signum() > 0;
    }

    @Override
    public int compareTo(Dinheiro outro) {
        return valor.compareTo(outro.valor);
    }

    @Override
    public String toString() {
        return "R$ " + valor.toPlainString().replace('.', ',');
    }
}
