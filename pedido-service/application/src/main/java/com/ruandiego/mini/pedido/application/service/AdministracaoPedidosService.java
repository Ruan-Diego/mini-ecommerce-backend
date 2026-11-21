package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.Pagina;
import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase;
import com.ruandiego.mini.pedido.application.port.in.ConsultarIndicadoresUseCase;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository.Criterios;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * Use cases do admin sobre pedidos. Separado do PedidoService, que é o lado do cliente: aqui não há dono do
 * pedido, e a autorização (papel admin) fica no adapter REST. Sem Spring: a transação vem do wiring.
 *
 * "Hoje" e as datas do filtro são do calendário da loja (`fuso`), não de UTC: um pedido às 22h de Brasília já
 * é o dia seguinte em UTC, e o painel tem de contar como a loja conta.
 */
public class AdministracaoPedidosService implements AdministrarPedidosUseCase, ConsultarIndicadoresUseCase {

    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    /** Pedidos que viraram dinheiro: pagos e o que vem depois. Cancelado não fatura (o pagamento é estornado). */
    static final Set<StatusPedido> FATURADOS = EnumSet.of(StatusPedido.PAGO, StatusPedido.EM_SEPARACAO,
            StatusPedido.ENVIADO, StatusPedido.ENTREGUE);

    private static final int LOTE_DO_FATURAMENTO = 500;

    private final PedidoRepository pedidos;
    private final PublicadorEventos publicador;
    private final Clock relogio;
    private final ZoneId fuso;

    public AdministracaoPedidosService(PedidoRepository pedidos, PublicadorEventos publicador, Clock relogio,
                                       ZoneId fuso) {
        this.pedidos = pedidos;
        this.publicador = publicador;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    @Override
    public Pagina<Pedido> listar(Filtro filtro, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new RegraDeNegocioException("página começa em 0");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new RegraDeNegocioException("tamanho da página deve ficar entre 1 e " + TAMANHO_MAXIMO_PAGINA);
        }
        if (filtro.de() != null && filtro.ate() != null && filtro.de().isAfter(filtro.ate())) {
            throw new RegraDeNegocioException("a data inicial do período é depois da final");
        }
        Criterios criterios = new Criterios(filtro.status(), inicioDoDia(filtro.de()),
                filtro.ate() == null ? null : inicioDoDia(filtro.ate().plusDays(1)));
        return pedidos.buscar(criterios, pagina, tamanho);
    }

    @Override
    public Pedido obter(PedidoId id) {
        return buscar(id);
    }

    /**
     * Traduz o status pedido pelo admin no método do domínio correspondente. A máquina de estados continua no
     * agregado: aqui só se impede o que é do pagamento (marcar como PAGO) e o que não é transição (voltar a
     * AGUARDANDO_PAGAMENTO). Cancelar publica `pedido.cancelado`, e a saga faz o resto (estoque devolve,
     * pagamento estorna), igual ao cancelamento pelo cliente.
     */
    @Override
    public Pedido alterarStatus(PedidoId id, StatusPedido destino, String codigoRastreio) {
        Pedido pedido = buscar(id);
        Instant agora = relogio.instant();
        switch (destino) {
            case EM_SEPARACAO -> pedido.iniciarSeparacao(agora);
            case ENVIADO -> pedido.enviar(codigoRastreio, agora);
            case ENTREGUE -> pedido.entregar(agora);
            case CANCELADO -> pedido.cancelar(agora);
            case PAGO -> throw new RegraDeNegocioException(
                    "o pedido só fica PAGO quando o pagamento é aprovado; não dá para marcar à mão");
            case AGUARDANDO_PAGAMENTO -> throw new RegraDeNegocioException(
                    "AGUARDANDO_PAGAMENTO é o status inicial; nenhum pedido volta para ele");
        }
        pedidos.salvar(pedido);
        publicador.publicar(pedido.eventosPendentes());
        pedido.limparEventos();
        return pedido;
    }

    @Override
    public Indicadores indicadores() {
        LocalDate hoje = LocalDate.now(relogio.withZone(fuso));
        Instant inicio = inicioDoDia(hoje);
        Instant fim = inicioDoDia(hoje.plusDays(1));

        long pedidosHoje = pedidos.contar(new Criterios(Set.of(), inicio, fim));
        long aguardando = pedidos.contar(new Criterios(Set.of(StatusPedido.AGUARDANDO_PAGAMENTO), null, null));
        Dinheiro faturamento = somarTotais(new Criterios(FATURADOS, inicio, fim));
        return new Indicadores(hoje, pedidosHoje, faturamento, aguardando);
    }

    /** O total é calculado pelo domínio (itens + frete), então soma-se página a página, sem duplicar a regra em SQL. */
    private Dinheiro somarTotais(Criterios criterios) {
        Dinheiro soma = Dinheiro.ZERO;
        int pagina = 0;
        Pagina<Pedido> lote;
        do {
            lote = pedidos.buscar(criterios, pagina++, LOTE_DO_FATURAMENTO);
            for (Pedido pedido : lote.itens()) {
                soma = soma.somar(pedido.total());
            }
        } while (pagina < lote.totalPaginas());
        return soma;
    }

    private Instant inicioDoDia(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(fuso).toInstant();
    }

    private Pedido buscar(PedidoId id) {
        return pedidos.buscarPorId(id).orElseThrow(() -> new NaoEncontradoException("pedido " + id + " não encontrado"));
    }
}
