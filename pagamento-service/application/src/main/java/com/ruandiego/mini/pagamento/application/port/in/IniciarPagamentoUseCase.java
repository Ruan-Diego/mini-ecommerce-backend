package com.ruandiego.mini.pagamento.application.port.in;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;

/** Inicia o pagamento de um pedido, no cartão (resposta na hora) ou por Pix (pendente até o webhook). */
public interface IniciarPagamentoUseCase {

    /** `numeroCartao` só vale para CARTAO: vai para o gateway e não é guardado nem registrado em lugar nenhum. */
    record Comando(ClienteId cliente, PedidoId pedido, Dinheiro valor, MetodoPagamento metodo, String numeroCartao) {
    }

    Pagamento iniciar(Comando comando);
}
