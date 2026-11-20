package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.ConflitoException;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.memoria.CalculadoraFreteEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.CatalogoConsultaEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase.Comando;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase.ItemSolicitado;
import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.TransicaoDeStatusInvalida;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Use cases do pedido sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class PedidoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");
    private static final ClienteId ANA = new ClienteId("ana");
    private static final ClienteId BETO = new ClienteId("beto");

    private final PedidoRepositoryEmMemoria pedidos = new PedidoRepositoryEmMemoria();
    private final CatalogoConsultaEmMemoria catalogo = new CatalogoConsultaEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final PedidoService service = new PedidoService(pedidos, new CalculadoraFreteEmMemoria(), catalogo, outbox,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private final ProdutoId teclado = new ProdutoId(UUID.randomUUID());
    private final ProdutoId mouse = new ProdutoId(UUID.randomUUID());

    @BeforeEach
    void catalogoComDoisProdutos() {
        catalogo.cadastrar(new CatalogoConsulta.Produto(teclado, "Teclado", Dinheiro.de("200.00")));
        catalogo.cadastrar(new CatalogoConsulta.Produto(mouse, "Mouse", Dinheiro.de("80.00")));
    }

    private static EnderecoEntrega endereco() {
        return new EnderecoEntrega("Ana Souza", "01310-100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP");
    }

    private Comando comando(ClienteId cliente, String frete, ItemSolicitado... itens) {
        return new Comando(cliente, List.of(itens), endereco(), frete);
    }

    private ItemSolicitado item(ProdutoId id, int quantidade, String precoVisto) {
        return new ItemSolicitado(id, quantidade, Dinheiro.de(precoVisto));
    }

    // ---- criar ----

    @Test
    void criaOPedidoComPrecoENomeDoCatalogoEFreteEscolhido() {
        Pedido pedido = service.criar(comando(ANA, "expresso", item(teclado, 1, "200.00"), item(mouse, 2, "80.00")));

        assertThat(pedido.status()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido.itens()).extracting(i -> i.nome()).containsExactly("Teclado", "Mouse");
        assertThat(pedido.subtotal()).isEqualTo(Dinheiro.de("360.00"));
        assertThat(pedido.frete().codigo()).isEqualTo("expresso");
        assertThat(pedido.total()).isEqualTo(Dinheiro.de("394.90"));
        assertThat(pedidos.buscarPorId(pedido.id())).isPresent();
    }

    @Test
    void gravaOEventoPedidoCriadoNaOutboxEnaoDeixaEventoPendenteNoAgregado() {
        Pedido pedido = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));

        assertThat(outbox.tiposGravados()).containsExactly("pedido.criado");
        assertThat(pedido.eventosPendentes()).isEmpty();
    }

    @Test
    void somaItensRepetidosDoMesmoProduto() {
        Pedido pedido = service.criar(comando(ANA, "padrao", item(mouse, 2, "80.00"), item(mouse, 3, "80.00")));

        assertThat(pedido.itens()).singleElement().satisfies(i -> assertThat(i.quantidade()).isEqualTo(5));
    }

    @Test
    void recusaQuandoOsItensRepetidosPassamDoLimiteDeDezUnidades() {
        assertThatThrownBy(() -> service.criar(comando(ANA, "padrao", item(mouse, 6, "80.00"), item(mouse, 6, "80.00"))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaComConflitoQuandoOPrecoDoCatalogoMudou() {
        catalogo.cadastrar(new CatalogoConsulta.Produto(teclado, "Teclado", Dinheiro.de("250.00")));

        assertThatThrownBy(() -> service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00"))))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Teclado");
        assertThat(pedidos.listarPorCliente(ANA)).isEmpty();
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void recusaProdutoQueNaoExisteNoCatalogo() {
        assertThatThrownBy(() -> service.criar(comando(ANA, "padrao", item(new ProdutoId(UUID.randomUUID()), 1, "10.00"))))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void recusaOpcaoDeFreteInexistente() {
        assertThatThrownBy(() -> service.criar(comando(ANA, "sedex-10", item(teclado, 1, "200.00"))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("frete");
    }

    @Test
    void recusaPedidoSemItens() {
        assertThatThrownBy(() -> service.criar(new Comando(ANA, List.of(), endereco(), "padrao")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // ---- consultar ----

    @Test
    void listaSoOsPedidosDoCliente() {
        Pedido deAna = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));
        service.criar(comando(BETO, "padrao", item(mouse, 1, "80.00")));

        assertThat(service.meusPedidos(ANA)).extracting(Pedido::id).containsExactly(deAna.id());
    }

    @Test
    void obterPedidoDeOutroClienteEhNaoEncontrado() {
        Pedido deAna = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));

        assertThat(service.obter(ANA, deAna.id()).id()).isEqualTo(deAna.id());
        assertThatThrownBy(() -> service.obter(BETO, deAna.id())).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.obter(ANA, PedidoId.novo())).isInstanceOf(NaoEncontradoException.class);
    }

    // ---- cancelar ----

    @Test
    void cancelaOPedidoDoClienteEGravaOEvento() {
        Pedido pedido = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));

        Pedido cancelado = service.cancelar(ANA, pedido.id());

        assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(outbox.tiposGravados()).containsExactly("pedido.criado", "pedido.cancelado");
    }

    @Test
    void naoCancelaPedidoDeOutroCliente() {
        Pedido pedido = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));

        assertThatThrownBy(() -> service.cancelar(BETO, pedido.id())).isInstanceOf(NaoEncontradoException.class);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().status()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void naoCancelaPedidoJaEnviadoENaoGravaEvento() {
        Pedido pedido = service.criar(comando(ANA, "padrao", item(teclado, 1, "200.00")));
        Pedido enviado = pedidos.buscarPorId(pedido.id()).orElseThrow();
        enviado.marcarComoPago(AGORA);
        enviado.iniciarSeparacao(AGORA);
        enviado.enviar("BR123456789", AGORA);
        pedidos.salvar(enviado);

        assertThatThrownBy(() -> service.cancelar(ANA, pedido.id())).isInstanceOf(TransicaoDeStatusInvalida.class);
        assertThat(pedidos.buscarPorId(pedido.id()).orElseThrow().status()).isEqualTo(StatusPedido.ENVIADO);
        assertThat(outbox.tiposGravados()).containsExactly("pedido.criado");
    }

    // ---- frete ----

    @Test
    void cotaFreteNormalizandoOCep() {
        assertThat(service.cotar("01310-100", Dinheiro.de("100.00"))).extracting(f -> f.codigo())
                .containsExactly("padrao", "expresso");
    }

    @Test
    void recusaCepInvalidoNaCotacao() {
        assertThatThrownBy(() -> service.cotar("123", Dinheiro.de("100.00"))).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.cotar(null, Dinheiro.de("100.00"))).isInstanceOf(RegraDeNegocioException.class);
    }
}
