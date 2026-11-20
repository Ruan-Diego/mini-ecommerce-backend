package com.ruandiego.mini.pagamento.adapters.out.gateway;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayFakeAdapterTest {

    private final GatewayFakeAdapter gateway = new GatewayFakeAdapter();

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111110", "4111111111111112", "4111111111111114", "4111111111111116", "4111111111111118"})
    void cartaoComFinalParAprova(String numero) {
        GatewayPagamento.ResultadoCartao resultado = gateway.cobrarCartao(Dinheiro.de("10.00"), numero);

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.motivoRecusa()).isNull();
        assertThat(resultado.transacaoId()).startsWith("fake-");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "4111111111111113", "4111111111111115", "4111111111111117", "4111111111111119"})
    void cartaoComFinalImparRecusa(String numero) {
        GatewayPagamento.ResultadoCartao resultado = gateway.cobrarCartao(Dinheiro.de("10.00"), numero);

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.motivoRecusa()).isNotBlank().doesNotContain(numero);
    }

    @Test
    void cadaCobrancaTemUmaTransacaoDiferente() {
        String a = gateway.cobrarCartao(Dinheiro.de("10.00"), "4111111111111112").transacaoId();
        String b = gateway.cobrarCartao(Dinheiro.de("10.00"), "4111111111111112").transacaoId();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void pixDevolveTransacaoECodigoCopiaECola() {
        GatewayPagamento.CobrancaPix pix = gateway.gerarPix(Dinheiro.de("219.90"), Instant.parse("2026-11-20T10:30:00Z"));

        assertThat(pix.transacaoId()).startsWith("fake-");
        assertThat(pix.codigoCopiaECola()).startsWith("00020126FAKE").contains("21990");
    }

    @Test
    void estornoEhAceito() {
        gateway.estornar("fake-qualquer");
    }
}
