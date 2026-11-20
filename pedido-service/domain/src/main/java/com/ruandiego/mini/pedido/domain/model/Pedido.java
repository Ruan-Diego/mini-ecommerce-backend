package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.AggregateRoot;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.TransicaoDeStatusInvalida;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Agregado Pedido. Nasce em AGUARDANDO_PAGAMENTO e só muda de status pelos métodos abaixo, que passam
 * todos por {@link #transicionar}: é lá que a máquina de estados é imposta e o evento é registrado.
 * Os eventos ficam no agregado e só são publicados depois do commit, via outbox.
 */
public class Pedido extends AggregateRoot {

    private final PedidoId id;
    private final ClienteId clienteId;
    private final List<ItemPedido> itens;
    private final EnderecoEntrega endereco;
    private final Frete frete;
    private final Instant criadoEm;
    private StatusPedido status;
    private Instant atualizadoEm;

    private Pedido(PedidoId id, ClienteId clienteId, List<ItemPedido> itens, EnderecoEntrega endereco, Frete frete,
                   StatusPedido status, Instant criadoEm, Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
        this.endereco = Objects.requireNonNull(endereco, "endereco");
        this.frete = Objects.requireNonNull(frete, "frete");
        this.itens = List.copyOf(itens);
        this.status = Objects.requireNonNull(status, "status");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm");
    }

    /** Cria um pedido novo. Registra {@link PedidoCriado}. */
    public static Pedido criar(ClienteId clienteId, List<ItemPedido> itens, EnderecoEntrega endereco, Frete frete,
                               Instant agora) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("o pedido precisa de pelo menos um item");
        }
        Pedido pedido = new Pedido(PedidoId.novo(), clienteId, itens, endereco, frete,
                StatusPedido.AGUARDANDO_PAGAMENTO, agora, agora);
        List<PedidoCriado.Item> itensDoEvento = itens.stream()
                .map(i -> new PedidoCriado.Item(i.produtoId().toString(), i.quantidade()))
                .toList();
        pedido.registrar(new PedidoCriado(pedido.id.toString(), clienteId.toString(), pedido.total().valor(),
                itensDoEvento, agora));
        return pedido;
    }

    /** Recria um pedido já persistido. Não registra evento: nada de novo aconteceu. */
    public static Pedido reconstituir(PedidoId id, ClienteId clienteId, List<ItemPedido> itens,
                                      EnderecoEntrega endereco, Frete frete, StatusPedido status,
                                      Instant criadoEm, Instant atualizadoEm) {
        return new Pedido(id, clienteId, itens, endereco, frete, status, criadoEm, atualizadoEm);
    }

    public void marcarComoPago(Instant agora) {
        transicionar(StatusPedido.PAGO, agora);
    }

    public void iniciarSeparacao(Instant agora) {
        transicionar(StatusPedido.EM_SEPARACAO, agora);
    }

    public void enviar(Instant agora) {
        transicionar(StatusPedido.ENVIADO, agora);
    }

    public void entregar(Instant agora) {
        transicionar(StatusPedido.ENTREGUE, agora);
    }

    public void cancelar(Instant agora) {
        transicionar(StatusPedido.CANCELADO, agora);
    }

    private void transicionar(StatusPedido destino, Instant agora) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoDeStatusInvalida(status, destino);
        }
        StatusPedido anterior = status;
        status = destino;
        atualizadoEm = agora;
        registrar(new PedidoStatusAlterado(id.toString(), clienteId.toString(), anterior.name(), destino.name(), agora));
    }

    public Dinheiro subtotal() {
        return itens.stream().map(ItemPedido::subtotal).reduce(Dinheiro.ZERO, Dinheiro::somar);
    }

    public Dinheiro total() {
        return subtotal().somar(frete.valor());
    }

    public PedidoId id() {
        return id;
    }

    public ClienteId clienteId() {
        return clienteId;
    }

    public List<ItemPedido> itens() {
        return itens;
    }

    public EnderecoEntrega endereco() {
        return endereco;
    }

    public Frete frete() {
        return frete;
    }

    public StatusPedido status() {
        return status;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
