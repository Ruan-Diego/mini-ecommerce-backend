package com.ruandiego.mini.carrinho.application.contrato;

import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do port CupomRepository. Quem estende prepara os dados e devolve o repositório:
 * precisa conter um percentual {@code BEMVINDO} (10%, mínimo R$ 100,00) e um de valor fixo {@code FRETE15}.
 */
public abstract class CupomRepositoryContrato {

    protected abstract CupomRepository cupons();

    @Test
    void buscaPorCodigoSemDiferenciarMaiusculasENemEspacos() {
        Cupom cupom = cupons().buscarPorCodigo("  bemvindo ").orElseThrow();

        assertThat(cupom.codigo()).isEqualTo("BEMVINDO");
        assertThat(cupom.tipo()).isEqualTo(Cupom.Tipo.PERCENTUAL);
        assertThat(cupom.valor()).isEqualByComparingTo("10");
        assertThat(cupom.valorMinimo().valor()).isEqualByComparingTo("100.00");
        assertThat(cupom.validoAte()).isNotNull();
    }

    @Test
    void cupomDeValorFixoVoltaComOValorEmReais() {
        Cupom cupom = cupons().buscarPorCodigo("FRETE15").orElseThrow();

        assertThat(cupom.tipo()).isEqualTo(Cupom.Tipo.VALOR_FIXO);
        assertThat(cupom.valor()).isEqualByComparingTo("15.00");
    }

    @Test
    void codigoDesconhecidoOuVazioViraOptionalVazio() {
        assertThat(cupons().buscarPorCodigo("NAO-EXISTE")).isEmpty();
        assertThat(cupons().buscarPorCodigo("  ")).isEmpty();
        assertThat(cupons().buscarPorCodigo(null)).isEmpty();
    }
}
