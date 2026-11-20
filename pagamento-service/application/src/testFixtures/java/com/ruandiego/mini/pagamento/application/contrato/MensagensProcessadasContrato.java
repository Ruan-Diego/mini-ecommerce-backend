package com.ruandiego.mini.pagamento.application.contrato;

import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do registro de mensagens tratadas. Quem estende devolve um registro vazio a cada teste. */
public abstract class MensagensProcessadasContrato {

    protected abstract MensagensProcessadas processadas();

    @Test
    void soAPrimeiraVezRegistra() {
        UUID mensagem = UUID.randomUUID();

        assertThat(processadas().registrar("consumidor-a", mensagem)).isTrue();
        assertThat(processadas().registrar("consumidor-a", mensagem)).isFalse();
        assertThat(processadas().registrar("consumidor-a", mensagem)).isFalse();
    }

    @Test
    void consumidoresDiferentesTratamAMesmaMensagemCadaUm() {
        UUID mensagem = UUID.randomUUID();

        assertThat(processadas().registrar("consumidor-a", mensagem)).isTrue();
        assertThat(processadas().registrar("consumidor-b", mensagem)).isTrue();
    }

    @Test
    void mensagensDiferentesSaoIndependentes() {
        assertThat(processadas().registrar("consumidor-a", UUID.randomUUID())).isTrue();
        assertThat(processadas().registrar("consumidor-a", UUID.randomUUID())).isTrue();
    }
}
