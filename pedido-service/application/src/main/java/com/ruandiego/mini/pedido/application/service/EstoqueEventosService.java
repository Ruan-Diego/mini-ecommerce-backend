package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDeEstoqueUseCase;
import com.ruandiego.mini.pedido.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;

import java.time.Clock;

/**
 * Reação do pedido aos eventos do estoque. Quando algo não bate (pedido inexistente, pedido já enviado),
 * lança: a mensagem é tentada 3 vezes e vai para a fila morta (DLQ), onde uma pessoa a inspeciona. O registro
 * da mensagem como tratada está na mesma transação do cancelamento, então uma falha desfaz os dois.
 */
public class EstoqueEventosService implements ProcessarEventoDeEstoqueUseCase {

    /** Nome deste consumidor no registro de mensagens tratadas (o mesmo do PagamentoEventosService: mensagens distintas). */
    public static final String CONSUMIDOR = "pedido-service";

    private final PedidoRepository pedidos;
    private final MensagensProcessadas processadas;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public EstoqueEventosService(PedidoRepository pedidos, MensagensProcessadas processadas,
                                 PublicadorEventos publicador, Clock relogio) {
        this.pedidos = pedidos;
        this.processadas = processadas;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public void indisponivel(EstoqueIndisponivel evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Pedido pedido = pedidos.buscarPorId(evento.pedido())
                .orElseThrow(() -> new NaoEncontradoException("pedido " + evento.pedido() + " não encontrado"));
        if (pedido.status() == StatusPedido.CANCELADO) {
            return; // já cancelado (pelo cliente, ou por outra mensagem): nada a fazer
        }
        // Pode estar PAGO: o cliente pagou antes de o estoque responder. Cancelar é o certo; o pagamento estorna
        // ao ver `pedido.cancelado`. Se já foi enviado, lança TransicaoDeStatusInvalida e a mensagem vai à DLQ.
        pedido.cancelar(relogio.instant());
        pedidos.salvar(pedido);
        publicador.publicar(pedido.eventosPendentes());
        pedido.limparEventos();
    }
}
