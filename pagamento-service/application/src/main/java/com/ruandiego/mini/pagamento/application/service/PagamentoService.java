package com.ruandiego.mini.pagamento.application.service;

import com.ruandiego.mini.pagamento.application.ConflitoException;
import com.ruandiego.mini.pagamento.application.NaoEncontradoException;
import com.ruandiego.mini.pagamento.application.port.in.ConsultarPagamentosUseCase;
import com.ruandiego.mini.pagamento.application.port.in.EstornarPagamentoUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ExpirarPixVencidosUseCase;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase;
import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pagamento.domain.RegraDeNegocioException;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implementa os use cases do pagamento. Não conhece Spring nem HTTP: orquestra domínio e ports. A transação
 * é aberta pelo wiring (PagamentoConfig): salvar o pagamento, registrar o webhook como tratado e gravar os
 * eventos na outbox acontecem juntos, ou nenhum dos três.
 */
public class PagamentoService implements IniciarPagamentoUseCase, ConsultarPagamentosUseCase,
        ProcessarEventoDoGatewayUseCase, EstornarPagamentoUseCase, ExpirarPixVencidosUseCase {

    private static final int LOTE_DE_EXPIRACAO = 100;

    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final EventosDoGatewayProcessados processados;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public PagamentoService(PagamentoRepository pagamentos, GatewayPagamento gateway,
                            EventosDoGatewayProcessados processados, PublicadorEventos publicador, Clock relogio) {
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.processados = processados;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public Pagamento iniciar(Comando comando) {
        Instant agora = relogio.instant();
        validarCartao(comando);
        impedirPagamentoEmDobro(comando.pedido(), agora);

        Pagamento pagamento;
        if (comando.metodo() == MetodoPagamento.CARTAO) {
            pagamento = Pagamento.cartao(comando.pedido(), comando.cliente(), comando.valor(), agora);
            GatewayPagamento.ResultadoCartao resultado = gateway.cobrarCartao(comando.valor(), comando.numeroCartao());
            pagamento.vincularTransacao(resultado.transacaoId());
            if (resultado.aprovado()) {
                pagamento.aprovar(agora);
            } else {
                pagamento.recusar(resultado.motivoRecusa(), agora);
            }
        } else {
            pagamento = Pagamento.pix(comando.pedido(), comando.cliente(), comando.valor(), agora);
            GatewayPagamento.CobrancaPix cobranca = gateway.gerarPix(comando.valor(), pagamento.pixExpiraEm());
            pagamento.vincularTransacao(cobranca.transacaoId());
            pagamento.definirCodigoPix(cobranca.codigoCopiaECola());
        }
        pagamentos.salvar(pagamento);
        registrarEventos(pagamento);
        return pagamento;
    }

    @Override
    public Pagamento obter(ClienteId cliente, PagamentoId id) {
        return pagamentos.buscarPorId(id)
                .filter(pagamento -> pagamento.clienteId().equals(cliente))
                .orElseThrow(() -> new NaoEncontradoException("pagamento " + id + " não encontrado"));
    }

    @Override
    public List<Pagamento> doPedido(ClienteId cliente, PedidoId pedido) {
        return pagamentos.listarPorPedido(pedido).stream()
                .filter(pagamento -> pagamento.clienteId().equals(cliente))
                .toList();
    }

    /**
     * Webhook do gateway. Duas travas tornam o reenvio inofensivo: se o pagamento já está no status do evento,
     * nada a fazer; e o registro (transação, evento) só deixa passar a primeira entrega, mesmo se duas
     * chegarem juntas. O registro e a mudança de status estão na mesma transação.
     */
    @Override
    public Resultado processar(String transacaoId, Evento evento) {
        Pagamento pagamento = pagamentos.buscarPorTransacao(transacaoId)
                .orElseThrow(() -> new NaoEncontradoException("transação " + transacaoId + " não encontrada"));
        if (pagamento.status() == statusDoEvento(evento)) {
            return Resultado.JA_PROCESSADO;
        }
        if (!processados.registrar(transacaoId, evento.name())) {
            return Resultado.JA_PROCESSADO;
        }
        Instant agora = relogio.instant();
        switch (evento) {
            case APROVADO -> pagamento.aprovar(agora);
            case RECUSADO -> pagamento.recusar("recusado pelo gateway", agora);
            case ESTORNADO -> pagamento.estornar(agora);
        }
        pagamentos.salvar(pagamento);
        registrarEventos(pagamento);
        return Resultado.PROCESSADO;
    }

    @Override
    public Pagamento estornar(PagamentoId id) {
        Pagamento pagamento = pagamentos.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("pagamento " + id + " não encontrado"));
        // Primeiro o domínio (recusa estornar o que não foi aprovado), depois o gateway: não se chama a
        // operadora por uma operação que a regra de negócio já vetou.
        pagamento.estornar(relogio.instant());
        gateway.estornar(pagamento.transacaoId());
        pagamentos.salvar(pagamento);
        registrarEventos(pagamento);
        return pagamento;
    }

    @Override
    public int expirarVencidos() {
        Instant agora = relogio.instant();
        List<Pagamento> vencidos = pagamentos.listarPixVencidos(agora, LOTE_DE_EXPIRACAO);
        for (Pagamento pagamento : vencidos) {
            pagamento.expirar(agora);
            pagamentos.salvar(pagamento);
            registrarEventos(pagamento);
        }
        return vencidos.size();
    }

    private static StatusPagamento statusDoEvento(Evento evento) {
        return switch (evento) {
            case APROVADO -> StatusPagamento.APROVADO;
            case RECUSADO -> StatusPagamento.RECUSADO;
            case ESTORNADO -> StatusPagamento.ESTORNADO;
        };
    }

    /** O número do cartão só é conferido no formato (13 a 19 dígitos) e nunca entra em mensagem de erro. */
    private static void validarCartao(Comando comando) {
        if (comando.metodo() != MetodoPagamento.CARTAO) {
            return;
        }
        String numero = comando.numeroCartao();
        if (numero == null || !numero.matches("[0-9]{13,19}")) {
            throw new RegraDeNegocioException("número do cartão deve ter de 13 a 19 dígitos");
        }
    }

    /** Pedido com pagamento aprovado, ou com Pix ainda válido, não aceita outro pagamento. */
    private void impedirPagamentoEmDobro(PedidoId pedido, Instant agora) {
        boolean jaPago = pagamentos.listarPorPedido(pedido).stream().anyMatch(p ->
                p.status() == StatusPagamento.APROVADO
                        || (p.status() == StatusPagamento.PENDENTE && !p.pixVencido(agora)));
        if (jaPago) {
            throw new ConflitoException("o pedido " + pedido + " já tem um pagamento aprovado ou em andamento");
        }
    }

    private void registrarEventos(Pagamento pagamento) {
        publicador.publicar(pagamento.eventosPendentes());
        pagamento.limparEventos();
    }
}
