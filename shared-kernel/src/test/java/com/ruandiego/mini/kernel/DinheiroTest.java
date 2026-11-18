package com.ruandiego.mini.kernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DinheiroTest {

    @Test
    void arredondaParaDuasCasas() {
        assertThat(Dinheiro.de("10.005").valor()).isEqualByComparingTo("10.00");
    }

    @Test
    void somaEMultiplica() {
        Dinheiro total = Dinheiro.de("289.00").somar(Dinheiro.de("219.00")).multiplicar(2);
        assertThat(total).isEqualTo(Dinheiro.de("1016.00"));
    }

    @Test
    void formataEmReais() {
        assertThat(Dinheiro.de("457.2")).hasToString("R$ 457,20");
    }
}
