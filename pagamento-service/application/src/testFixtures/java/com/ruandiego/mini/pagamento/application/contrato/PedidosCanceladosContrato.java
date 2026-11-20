package com.ruandiego.mini.pagamento.application.contrato;

import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do registro de pedidos cancelados. Quem estende devolve um registro vazio a cada teste. */
public abstract class PedidosCanceladosContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PedidosCancelados cancelados();

    @Test
    void pedidoNuncaRegistradoNaoEstaCancelado() {
        assertThat(cancelados().estaCancelado(new PedidoId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void pedidoRegistradoEstaCanceladoEOutroNao() {
        PedidoId cancelado = new PedidoId(UUID.randomUUID());

        cancelados().registrar(cancelado, T0);

        assertThat(cancelados().estaCancelado(cancelado)).isTrue();
        assertThat(cancelados().estaCancelado(new PedidoId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void registrarDeNovoNaoFalhaENaoMudaOResultado() {
        PedidoId pedido = new PedidoId(UUID.randomUUID());

        cancelados().registrar(pedido, T0);
        cancelados().registrar(pedido, T0.plusSeconds(60));

        assertThat(cancelados().estaCancelado(pedido)).isTrue();
    }
}
