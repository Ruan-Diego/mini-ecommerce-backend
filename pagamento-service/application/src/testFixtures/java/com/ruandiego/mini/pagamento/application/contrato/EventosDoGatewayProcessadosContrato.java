package com.ruandiego.mini.pagamento.application.contrato;

import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do registro de eventos do gateway. Quem estende devolve um registro vazio a cada teste. */
public abstract class EventosDoGatewayProcessadosContrato {

    protected abstract EventosDoGatewayProcessados processados();

    @Test
    void soAPrimeiraVezRegistra() {
        assertThat(processados().registrar("tx-1", "APROVADO")).isTrue();
        assertThat(processados().registrar("tx-1", "APROVADO")).isFalse();
        assertThat(processados().registrar("tx-1", "APROVADO")).isFalse();
    }

    @Test
    void eventosDiferentesDaMesmaTransacaoSaoIndependentes() {
        assertThat(processados().registrar("tx-1", "APROVADO")).isTrue();
        assertThat(processados().registrar("tx-1", "ESTORNADO")).isTrue();
    }

    @Test
    void transacoesDiferentesSaoIndependentes() {
        assertThat(processados().registrar("tx-1", "APROVADO")).isTrue();
        assertThat(processados().registrar("tx-2", "APROVADO")).isTrue();
    }
}
