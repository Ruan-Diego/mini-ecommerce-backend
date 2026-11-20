package com.ruandiego.mini.pagamento.application.service;

import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDePedidoUseCase;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;

import java.time.Clock;

/**
 * Reação do pagamento aos eventos do pedido. A transação é aberta pelo wiring: estornar no domínio, gravar o
 * pagamento, registrar a mensagem como tratada e publicar o evento acontecem juntos, ou nenhum. Se o gateway
 * estiver fora do ar, a chamada lança, tudo é desfeito (inclusive o registro da mensagem) e o broker tenta de
 * novo 3 vezes antes de mandar a mensagem para a fila morta (DLQ).
 */
public class PedidoEventosService implements ProcessarEventoDePedidoUseCase {

    /** Nome deste consumidor no registro de mensagens tratadas. */
    public static final String CONSUMIDOR = "pagamento-service";

    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final MensagensProcessadas processadas;
    private final PedidosCancelados cancelados;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public PedidoEventosService(PagamentoRepository pagamentos, GatewayPagamento gateway,
                                MensagensProcessadas processadas, PedidosCancelados cancelados,
                                PublicadorEventos publicador, Clock relogio) {
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.processadas = processadas;
        this.cancelados = cancelados;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public void cancelado(PedidoCancelado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        // Guarda que o pedido foi cancelado: daqui em diante nenhuma tentativa de pagamento dele é aceita.
        cancelados.registrar(evento.pedido(), relogio.instant());
        for (Pagamento pagamento : pagamentos.listarPorPedido(evento.pedido())) {
            if (pagamento.status() != StatusPagamento.APROVADO) {
                continue; // recusado, expirado, ainda pendente ou já estornado: não há dinheiro a devolver
            }
            // Primeiro o domínio, depois o gateway (como no estorno do administrador): a operadora só é chamada
            // por um estorno que a regra de negócio já aceitou.
            pagamento.estornar(relogio.instant());
            gateway.estornar(pagamento.transacaoId());
            pagamentos.salvar(pagamento);
            publicador.publicar(pagamento.eventosPendentes());
            pagamento.limparEventos();
        }
    }
}
