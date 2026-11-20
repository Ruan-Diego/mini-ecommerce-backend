package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.Pagina;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase;
import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase.Filtro;
import com.ruandiego.mini.pedido.application.port.in.ConsultarIndicadoresUseCase.Indicadores;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.TransicaoDeStatusInvalida;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Use cases do admin sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class AdministracaoPedidosServiceTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    /** 20/11/2026 às 15h em Brasília (18h UTC). */
    private static final Instant AGORA = Instant.parse("2026-11-20T18:00:00Z");

    private final PedidoRepositoryEmMemoria pedidos = new PedidoRepositoryEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final AdministracaoPedidosService service = new AdministracaoPedidosService(pedidos, outbox,
            Clock.fixed(AGORA, BRASILIA), BRASILIA);

    /** Pedido de 100,00 + 10,00 de frete = 110,00, criado em `criadoEm` e levado até `status`. */
    private Pedido pedido(Instant criadoEm, StatusPedido status) {
        Pedido pedido = Pedido.criar(new ClienteId("ana"),
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Teclado", Dinheiro.de("50.00"), 2)),
                new EnderecoEntrega("Ana", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("10.00"), 5), criadoEm);
        switch (status) {
            case AGUARDANDO_PAGAMENTO -> { }
            case CANCELADO -> pedido.cancelar(criadoEm);
            default -> {
                pedido.marcarComoPago(criadoEm);
                if (status != StatusPedido.PAGO) {
                    pedido.iniciarSeparacao(criadoEm);
                }
                if (status == StatusPedido.ENVIADO || status == StatusPedido.ENTREGUE) {
                    pedido.enviar("BR1", criadoEm);
                }
                if (status == StatusPedido.ENTREGUE) {
                    pedido.entregar(criadoEm);
                }
            }
        }
        pedido.limparEventos();
        pedidos.salvar(pedido);
        return pedido;
    }

    // ---- indicadores ----

    @Test
    void indicadoresContamODiaDaLojaENaoODiaUtc() {
        // 20/11 00:30 em Brasília = 03:30 UTC: é hoje. 19/11 23:30 em Brasília = 20/11 02:30 UTC: é ontem.
        pedido(Instant.parse("2026-11-20T03:30:00Z"), StatusPedido.PAGO);
        pedido(Instant.parse("2026-11-20T02:30:00Z"), StatusPedido.PAGO);

        Indicadores indicadores = service.indicadores();

        assertThat(indicadores.dia()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(indicadores.pedidosHoje()).isEqualTo(1);
        assertThat(indicadores.faturamentoDoDia()).isEqualTo(Dinheiro.de("110.00"));
    }

    @Test
    void faturamentoSomaSoOsPagosEmDianteEAguardandoContaQualquerDia() {
        Instant hoje = Instant.parse("2026-11-20T12:00:00Z");
        Instant ontem = Instant.parse("2026-11-19T12:00:00Z");
        pedido(hoje, StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido(hoje, StatusPedido.CANCELADO);
        pedido(hoje, StatusPedido.PAGO);
        pedido(hoje, StatusPedido.EM_SEPARACAO);
        pedido(hoje, StatusPedido.ENVIADO);
        pedido(hoje, StatusPedido.ENTREGUE);
        pedido(ontem, StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido(ontem, StatusPedido.PAGO);

        Indicadores indicadores = service.indicadores();

        assertThat(indicadores.pedidosHoje()).isEqualTo(6);
        assertThat(indicadores.faturamentoDoDia()).isEqualTo(Dinheiro.de("440.00"));
        assertThat(indicadores.aguardandoPagamento()).isEqualTo(2);
    }

    @Test
    void indicadoresSemPedidosSaoZero() {
        Indicadores indicadores = service.indicadores();

        assertThat(indicadores.pedidosHoje()).isZero();
        assertThat(indicadores.faturamentoDoDia()).isEqualTo(Dinheiro.ZERO);
        assertThat(indicadores.aguardandoPagamento()).isZero();
    }

    @Test
    void faturamentoPassaDeUmLoteDeLeitura() {
        Instant hoje = Instant.parse("2026-11-20T12:00:00Z");
        for (int i = 0; i < 501; i++) {
            pedido(hoje.plusMillis(i), StatusPedido.PAGO);
        }

        assertThat(service.indicadores().faturamentoDoDia()).isEqualTo(Dinheiro.de("55110.00"));
    }

    // ---- listar ----

    @Test
    void listaFiltrandoPorStatusEPeriodoEmDatasDaLojaInclusivas() {
        Pedido dia18 = pedido(Instant.parse("2026-11-18T15:00:00Z"), StatusPedido.PAGO);
        Pedido inicioDia19 = pedido(Instant.parse("2026-11-19T03:00:00Z"), StatusPedido.PAGO); // 19/11 00:00 BRT
        Pedido fimDia19 = pedido(Instant.parse("2026-11-20T02:59:59Z"), StatusPedido.PAGO);    // 19/11 23:59 BRT
        pedido(Instant.parse("2026-11-20T03:00:00Z"), StatusPedido.PAGO);                      // 20/11 00:00 BRT
        pedido(Instant.parse("2026-11-19T15:00:00Z"), StatusPedido.CANCELADO);

        LocalDate dia19 = LocalDate.of(2026, 11, 19);
        Pagina<Pedido> doDia19 = service.listar(new Filtro(Set.of(StatusPedido.PAGO), dia19, dia19), 0, 20);
        Pagina<Pedido> ate19 = service.listar(new Filtro(Set.of(StatusPedido.PAGO), null, dia19), 0, 20);

        assertThat(doDia19.itens()).extracting(Pedido::id).containsExactly(fimDia19.id(), inicioDia19.id());
        assertThat(ate19.itens()).extracting(Pedido::id).containsExactly(fimDia19.id(), inicioDia19.id(), dia18.id());
        assertThat(service.listar(new Filtro(Set.of(), null, null), 0, 20).total()).isEqualTo(5);
    }

    @Test
    void recusaPeriodoInvertidoEPaginaForaDosLimites() {
        LocalDate dia = LocalDate.of(2026, 11, 20);
        Filtro todos = new Filtro(Set.of(), null, null);

        assertThatThrownBy(() -> service.listar(new Filtro(Set.of(), dia, dia.minusDays(1)), 0, 10))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.listar(todos, -1, 10)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.listar(todos, 0, 0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.listar(todos, 0, 101)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void obterPedidoInexistenteEhNaoEncontrado() {
        assertThatThrownBy(() -> service.obter(PedidoId.novo())).isInstanceOf(NaoEncontradoException.class);
    }

    // ---- alterar status ----

    @Test
    void levaOPedidoPagoAteEntregueGravandoOsEventosNaOutbox() {
        Pedido pedido = pedido(AGORA, StatusPedido.PAGO);

        service.alterarStatus(pedido.id(), StatusPedido.EM_SEPARACAO, null);
        service.alterarStatus(pedido.id(), StatusPedido.ENVIADO, "BR123456789");
        Pedido entregue = service.alterarStatus(pedido.id(), StatusPedido.ENTREGUE, null);

        assertThat(entregue.status()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().codigoRastreio()).isEqualTo("BR123456789");
        assertThat(outbox.tiposGravados())
                .containsExactly("pedido.em-separacao", "pedido.enviado", "pedido.entregue");
    }

    @Test
    void enviarSemRastreioEhRecusadoENadaMuda() {
        Pedido pedido = pedido(AGORA, StatusPedido.EM_SEPARACAO);

        assertThatThrownBy(() -> service.alterarStatus(pedido.id(), StatusPedido.ENVIADO, " "))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().status()).isEqualTo(StatusPedido.EM_SEPARACAO);
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void transicaoForaDaMaquinaDeEstadosEhRecusada() {
        Pedido pedido = pedido(AGORA, StatusPedido.AGUARDANDO_PAGAMENTO);

        assertThatThrownBy(() -> service.alterarStatus(pedido.id(), StatusPedido.ENVIADO, "BR1"))
                .isInstanceOf(TransicaoDeStatusInvalida.class);
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatusPedido.class, names = {"PAGO", "AGUARDANDO_PAGAMENTO"})
    void adminNaoMarcaComoPagoNemVoltaParaAguardando(StatusPedido destino) {
        Pedido pedido = pedido(AGORA, StatusPedido.AGUARDANDO_PAGAMENTO);

        assertThatThrownBy(() -> service.alterarStatus(pedido.id(), destino, null))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().status())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void cancelarPeloAdminPublicaPedidoCanceladoParaASaga() {
        Pedido pedido = pedido(AGORA, StatusPedido.PAGO);

        service.alterarStatus(pedido.id(), StatusPedido.CANCELADO, null);

        assertThat(outbox.tiposGravados()).containsExactly("pedido.cancelado");
    }

    @Test
    void alterarPedidoInexistenteEhNaoEncontrado() {
        assertThatThrownBy(() -> service.alterarStatus(PedidoId.novo(), StatusPedido.CANCELADO, null))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void transicoesManuaisNuncaOferecemPago() {
        assertThat(AdministrarPedidosUseCase.transicoesManuais(StatusPedido.AGUARDANDO_PAGAMENTO))
                .containsExactly(StatusPedido.CANCELADO);
        assertThat(AdministrarPedidosUseCase.transicoesManuais(StatusPedido.PAGO))
                .containsExactlyInAnyOrder(StatusPedido.EM_SEPARACAO, StatusPedido.CANCELADO);
        assertThat(AdministrarPedidosUseCase.transicoesManuais(StatusPedido.ENTREGUE)).isEmpty();
    }
}
