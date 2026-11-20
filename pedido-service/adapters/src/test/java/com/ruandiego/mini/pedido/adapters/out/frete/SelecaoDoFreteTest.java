package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** A troca de provedor é só a propriedade `frete.provedor`: sem ela, ou com `fixo`, vale o frete fixo. */
class SelecaoDoFreteTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(FreteFixoAdapter.class, FreteConfig.class)
            .withPropertyValues("frete.melhorenvio.token=t", "frete.melhorenvio.cep-origem=01001000");

    @Test
    void semPropriedadeVaiDeFixo() {
        contexto.run(c -> assertThat(c.getBean(CalculadoraFrete.class)).isInstanceOf(FreteFixoAdapter.class));
    }

    @Test
    void fixoExplicito() {
        contexto.withPropertyValues("frete.provedor=fixo")
                .run(c -> assertThat(c.getBean(CalculadoraFrete.class)).isInstanceOf(FreteFixoAdapter.class));
    }

    @Test
    void melhorenvioTrocaOAdapterSemDeixarDoisBeans() {
        contexto.withPropertyValues("frete.provedor=melhorenvio").run(c -> {
            assertThat(c).hasSingleBean(CalculadoraFrete.class);
            assertThat(c.getBean(CalculadoraFrete.class)).isInstanceOf(FreteComFallback.class);
        });
    }
}
