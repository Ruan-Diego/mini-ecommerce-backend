package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase;
import com.ruandiego.mini.pedido.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;

import java.time.Clock;

/**
 * Reação do pedido aos eventos do pagamento. Quando algo não bate (pedido inexistente, valor diferente,
 * pedido já enviado), lança: a mensagem é tentada 3 vezes e vai para a fila morta (DLQ), onde uma pessoa a
 * inspeciona. O registro da mensagem como tratada está na mesma transação da mudança de status, então uma
 * falha desfaz os dois e a mensagem pode ser tentada de novo.
 */
public class PagamentoEventosService implements ProcessarEventoDePagamentoUseCase {

    /** Nome deste consumidor no registro de mensagens tratadas. */
    public static final String CONSUMIDOR = "pedido-service";

    private final PedidoRepository pedidos;
    private final MensagensProcessadas processadas;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public PagamentoEventosService(PedidoRepository pedidos, MensagensProcessadas processadas,
                                   PublicadorEventos publicador, Clock relogio) {
        this.pedidos = pedidos;
        this.processadas = processadas;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public void aprovado(PagamentoAprovado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Pedido pedido = buscar(evento.pedido());
        // O valor e o dono vêm do pagamento, que os recebeu do cliente: conferir contra o pedido é o que
        // impede pagar R$ 1,00 por um pedido de R$ 1.000,00, ou pagar o pedido de outra pessoa.
        if (!pedido.clienteId().equals(evento.cliente())) {
            throw new RegraDeNegocioException("o pagamento é de outro cliente");
        }
        if (!pedido.total().equals(evento.valor())) {
            throw new RegraDeNegocioException("o valor pago (" + evento.valor() + ") não é o total do pedido ("
                    + pedido.total() + ")");
        }
        if (pedido.status() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            return; // já foi tratado por outra mensagem (ex.: pagamento reprocessado); nada a fazer
        }
        pedido.marcarComoPago(relogio.instant());
        salvarEPublicar(pedido);
    }

    @Override
    public void estornado(PagamentoEstornado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Pedido pedido = buscar(evento.pedido());
        if (pedido.status() == StatusPedido.CANCELADO) {
            return;
        }
        pedido.cancelar(relogio.instant()); // lança TransicaoDeStatusInvalida se já foi enviado
        salvarEPublicar(pedido);
    }

    @Override
    public void recusado(PagamentoRecusado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Pedido pedido = buscar(evento.pedido());
        if (pedido.status() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            // Já cancelado, ou já pago por outra tentativa: uma recusa antiga não pode cancelar um pedido pago.
            return;
        }
        pedido.cancelar(relogio.instant());
        salvarEPublicar(pedido);
    }

    private Pedido buscar(PedidoId id) {
        return pedidos.buscarPorId(id).orElseThrow(() -> new NaoEncontradoException("pedido " + id + " não encontrado"));
    }

    private void salvarEPublicar(Pedido pedido) {
        pedidos.salvar(pedido);
        publicador.publicar(pedido.eventosPendentes());
        pedido.limparEventos();
    }
}
