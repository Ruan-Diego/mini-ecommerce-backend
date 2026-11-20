package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.ConflitoException;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.port.in.CancelarPedidoUseCase;
import com.ruandiego.mini.pedido.application.port.in.ConsultarPedidosUseCase;
import com.ruandiego.mini.pedido.application.port.in.CotarFreteUseCase;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase;
import com.ruandiego.mini.pedido.application.port.in.ExpirarPedidosSemPagamentoUseCase;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementa os use cases do pedido. Não conhece Spring nem HTTP: orquestra domínio e ports. A transação
 * é aberta pelo wiring (PedidoConfig); por isso salvar o pedido e registrar os eventos na outbox acontecem
 * juntos, ou nenhum dos dois.
 */
public class PedidoService implements CriarPedidoUseCase, ConsultarPedidosUseCase, CancelarPedidoUseCase,
        CotarFreteUseCase, ExpirarPedidosSemPagamentoUseCase {

    /** Quanto tempo um pedido espera o pagamento antes de ser cancelado (a mesma validade da reserva do estoque). */
    public static final Duration PRAZO_DE_PAGAMENTO = Duration.ofMinutes(30);

    private static final int LOTE_DE_EXPIRACAO = 100;

    private final PedidoRepository pedidos;
    private final CalculadoraFrete calculadoraFrete;
    private final CatalogoConsulta catalogo;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public PedidoService(PedidoRepository pedidos, CalculadoraFrete calculadoraFrete, CatalogoConsulta catalogo,
                         PublicadorEventos publicador, Clock relogio) {
        this.pedidos = pedidos;
        this.calculadoraFrete = calculadoraFrete;
        this.catalogo = catalogo;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public Pedido criar(Comando comando) {
        if (comando.itens() == null || comando.itens().isEmpty()) {
            throw new RegraDeNegocioException("o pedido precisa de pelo menos um item");
        }
        List<ItemPedido> itens = congelarItens(comando.itens());
        Dinheiro subtotal = itens.stream().map(ItemPedido::subtotal).reduce(Dinheiro.ZERO, Dinheiro::somar);
        Frete frete = escolherFrete(comando.endereco().cep(), subtotal, comando.freteCodigo());

        Pedido pedido = Pedido.criar(comando.cliente(), itens, comando.endereco(), frete, relogio.instant());
        pedidos.salvar(pedido);
        registrarEventos(pedido);
        return pedido;
    }

    @Override
    public List<Pedido> meusPedidos(ClienteId cliente) {
        return pedidos.listarPorCliente(cliente);
    }

    @Override
    public Pedido obter(ClienteId cliente, PedidoId id) {
        return buscarDoCliente(cliente, id);
    }

    @Override
    public Pedido cancelar(ClienteId cliente, PedidoId id) {
        Pedido pedido = buscarDoCliente(cliente, id);
        pedido.cancelar(relogio.instant());
        pedidos.salvar(pedido);
        registrarEventos(pedido);
        return pedido;
    }

    @Override
    public List<Frete> cotar(String cep, Dinheiro subtotal) {
        String digitos = cep == null ? "" : cep.replaceAll("[^0-9]", "");
        if (digitos.length() != 8) {
            throw new RegraDeNegocioException("CEP deve ter 8 dígitos");
        }
        return calculadoraFrete.cotar(digitos, subtotal);
    }

    /**
     * Lê nome e preço do catálogo (nunca do navegador) e confere o preço que o cliente viu. Itens repetidos
     * são somados, para a regra de 1 a 10 unidades valer por produto.
     */
    private List<ItemPedido> congelarItens(List<ItemSolicitado> solicitados) {
        Map<ProdutoId, ItemSolicitado> somados = new LinkedHashMap<>();
        for (ItemSolicitado item : solicitados) {
            somados.merge(item.produtoId(), item, (a, b) ->
                    new ItemSolicitado(a.produtoId(), a.quantidade() + b.quantidade(), a.precoVisto()));
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemSolicitado item : somados.values()) {
            CatalogoConsulta.Produto produto = catalogo.buscar(item.produtoId())
                    .orElseThrow(() -> new NaoEncontradoException(
                            "produto " + item.produtoId() + " não existe ou não está à venda"));
            if (!produto.preco().equals(item.precoVisto())) {
                throw new ConflitoException("o preço de \"" + produto.nome() + "\" mudou de "
                        + item.precoVisto() + " para " + produto.preco() + "; revise o carrinho");
            }
            itens.add(new ItemPedido(produto.id(), produto.nome(), produto.preco(), item.quantidade()));
        }
        return itens;
    }

    /**
     * Cancela, em uma transação só, até {@value #LOTE_DE_EXPIRACAO} pedidos que passaram de 30 minutos sem
     * pagamento. O relógio é o injetado, então o teste controla o "agora". Se o pagamento aprovado chegar
     * entre a leitura e a gravação, a versão do pedido muda (controle otimista no adapter), a gravação falha, a
     * transação desfaz tudo e a próxima rodada reavalia o que sobrou: nunca se cancela um pedido recém-pago.
     */
    @Override
    public int expirarVencidos() {
        Instant agora = relogio.instant();
        List<Pedido> vencidos = pedidos.listarAguardandoPagamentoCriadosAte(agora.minus(PRAZO_DE_PAGAMENTO),
                LOTE_DE_EXPIRACAO);
        for (Pedido pedido : vencidos) {
            pedido.cancelar(agora);
            pedidos.salvar(pedido);
            registrarEventos(pedido);
        }
        return vencidos.size();
    }

    private Frete escolherFrete(String cep, Dinheiro subtotal, String codigo) {
        return calculadoraFrete.cotar(cep, subtotal).stream()
                .filter(opcao -> opcao.codigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("opção de frete inválida para este CEP: " + codigo));
    }

    private void registrarEventos(Pedido pedido) {
        publicador.publicar(pedido.eventosPendentes());
        pedido.limparEventos();
    }

    private Pedido buscarDoCliente(ClienteId cliente, PedidoId id) {
        return pedidos.buscarPorId(id)
                .filter(pedido -> pedido.clienteId().equals(cliente))
                .orElseThrow(() -> new NaoEncontradoException("pedido " + id + " não encontrado"));
    }
}
