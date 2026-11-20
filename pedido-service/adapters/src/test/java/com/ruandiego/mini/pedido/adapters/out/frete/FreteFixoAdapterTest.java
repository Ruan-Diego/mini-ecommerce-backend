package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.Frete;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreteFixoAdapterTest {

    private final FreteFixoAdapter calculadora = new FreteFixoAdapter();

    @Test
    void oferecePadraoEExpressoComPrecosFixos() {
        List<Frete> opcoes = calculadora.cotar("01310100", Dinheiro.de("100.00"));

        assertThat(opcoes).extracting(Frete::codigo).containsExactly("padrao", "expresso");
        assertThat(opcoes.get(0).valor()).isEqualTo(Dinheiro.de("19.90"));
        assertThat(opcoes.get(0).prazoDias()).isEqualTo(7);
        assertThat(opcoes.get(1).valor()).isEqualTo(Dinheiro.de("34.90"));
        assertThat(opcoes.get(1).prazoDias()).isEqualTo(3);
    }

    @Test
    void padraoFicaGratisAPartirDe299Reais() {
        assertThat(calculadora.cotar("01310100", Dinheiro.de("298.99")).get(0).valor()).isEqualTo(Dinheiro.de("19.90"));
        assertThat(calculadora.cotar("01310100", Dinheiro.de("299.00")).get(0).valor()).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void oExpressoNuncaEhGratis() {
        assertThat(calculadora.cotar("01310100", Dinheiro.de("5000.00")).get(1).valor()).isEqualTo(Dinheiro.de("34.90"));
    }
}
