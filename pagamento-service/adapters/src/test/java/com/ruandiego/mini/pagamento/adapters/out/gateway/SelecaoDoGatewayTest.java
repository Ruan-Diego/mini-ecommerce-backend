package com.ruandiego.mini.pagamento.adapters.out.gateway;

import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** A troca de gateway é só a propriedade `pagamento.gateway`: sem ela, ou com `fake`, vale o fake. */
class SelecaoDoGatewayTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(GatewayFakeAdapter.class, MercadoPagoSandboxAdapter.class)
            .withPropertyValues("pagamento.mercadopago.access-token=TEST-t", "pagamento.mercadopago.public-key=TEST-p");

    @Test
    void semPropriedadeVaiDeFake() {
        contexto.run(c -> assertThat(c).hasSingleBean(GatewayPagamento.class).hasSingleBean(GatewayFakeAdapter.class));
    }

    @Test
    void fakeExplicito() {
        contexto.withPropertyValues("pagamento.gateway=fake")
                .run(c -> assertThat(c.getBean(GatewayPagamento.class)).isInstanceOf(GatewayFakeAdapter.class));
    }

    @Test
    void mercadopagoTrocaOAdapter() {
        contexto.withPropertyValues("pagamento.gateway=mercadopago")
                .run(c -> {
                    assertThat(c).hasSingleBean(GatewayPagamento.class);
                    assertThat(c.getBean(GatewayPagamento.class)).isInstanceOf(MercadoPagoSandboxAdapter.class);
                    assertThat(c).doesNotHaveBean(GatewayFakeAdapter.class);
                });
    }
}
