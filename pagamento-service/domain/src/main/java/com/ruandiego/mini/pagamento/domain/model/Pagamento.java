package com.ruandiego.mini.pagamento.domain.model;

import com.ruandiego.mini.kernel.AggregateRoot;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.domain.RegraDeNegocioException;
import com.ruandiego.mini.pagamento.domain.TransicaoDeStatusInvalida;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Agregado Pagamento. Nasce PENDENTE e só muda de status por {@link #aprovar}, {@link #recusar},
 * {@link #expirar} e {@link #estornar}, que passam por {@link #transicionar}: é lá que a máquina de estados
 * é imposta e o evento correspondente é registrado. Os eventos só são publicados depois do commit, via outbox.
 *
 * O número do cartão NUNCA entra aqui: o domínio guarda só o que o negócio precisa (valor, método, id da
 * transação no gateway). O número vai direto da requisição para o gateway e não é gravado em lugar nenhum.
 */
public class Pagamento extends AggregateRoot {

    /** Validade do código Pix: 30 minutos a partir da criação. */
    public static final Duration VALIDADE_DO_PIX = Duration.ofMinutes(30);

    private final PagamentoId id;
    private final PedidoId pedidoId;
    private final ClienteId clienteId;
    private final MetodoPagamento metodo;
    private final Dinheiro valor;
    private final Instant criadoEm;
    private final Instant pixExpiraEm;
    private StatusPagamento status;
    private String transacaoId;
    private String pixCodigo;
    private String motivoRecusa;
    private Instant atualizadoEm;

    private Pagamento(PagamentoId id, PedidoId pedidoId, ClienteId clienteId, MetodoPagamento metodo, Dinheiro valor,
                      StatusPagamento status, String transacaoId, String pixCodigo, Instant pixExpiraEm,
                      String motivoRecusa, Instant criadoEm, Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.pedidoId = Objects.requireNonNull(pedidoId, "pedidoId");
        this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
        this.metodo = Objects.requireNonNull(metodo, "metodo");
        this.valor = Objects.requireNonNull(valor, "valor");
        if (!valor.ehPositivo()) {
            throw new RegraDeNegocioException("o valor do pagamento deve ser maior que zero");
        }
        this.status = Objects.requireNonNull(status, "status");
        this.transacaoId = transacaoId;
        this.pixCodigo = pixCodigo;
        this.pixExpiraEm = pixExpiraEm;
        this.motivoRecusa = motivoRecusa;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm");
    }

    /** Pagamento no cartão, pendente até o gateway responder. */
    public static Pagamento cartao(PedidoId pedidoId, ClienteId clienteId, Dinheiro valor, Instant agora) {
        return new Pagamento(PagamentoId.novo(), pedidoId, clienteId, MetodoPagamento.CARTAO, valor,
                StatusPagamento.PENDENTE, null, null, null, null, agora, agora);
    }

    /** Pagamento por Pix, pendente por até 30 minutos. */
    public static Pagamento pix(PedidoId pedidoId, ClienteId clienteId, Dinheiro valor, Instant agora) {
        return new Pagamento(PagamentoId.novo(), pedidoId, clienteId, MetodoPagamento.PIX, valor,
                StatusPagamento.PENDENTE, null, null, agora.plus(VALIDADE_DO_PIX), null, agora, agora);
    }

    /** Recria um pagamento já persistido. Não registra evento: nada de novo aconteceu. */
    public static Pagamento reconstituir(PagamentoId id, PedidoId pedidoId, ClienteId clienteId,
                                         MetodoPagamento metodo, Dinheiro valor, StatusPagamento status,
                                         String transacaoId, String pixCodigo, Instant pixExpiraEm,
                                         String motivoRecusa, Instant criadoEm, Instant atualizadoEm) {
        return new Pagamento(id, pedidoId, clienteId, metodo, valor, status, transacaoId, pixCodigo, pixExpiraEm,
                motivoRecusa, criadoEm, atualizadoEm);
    }

    /** Guarda o id que o gateway deu à transação. Só uma vez: é por ele que o webhook acha o pagamento. */
    public void vincularTransacao(String transacaoId) {
        if (transacaoId == null || transacaoId.isBlank()) {
            throw new RegraDeNegocioException("id da transação é obrigatório");
        }
        if (this.transacaoId != null) {
            throw new RegraDeNegocioException("o pagamento já tem uma transação");
        }
        this.transacaoId = transacaoId;
    }

    /** Guarda o código "copia e cola" do Pix. Só para pagamentos Pix. */
    public void definirCodigoPix(String codigo) {
        if (metodo != MetodoPagamento.PIX) {
            throw new RegraDeNegocioException("só pagamento Pix tem código Pix");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("código Pix é obrigatório");
        }
        this.pixCodigo = codigo;
    }

    public void aprovar(Instant agora) {
        transicionar(StatusPagamento.APROVADO, agora);
        registrar(new PagamentoAprovado(id.toString(), pedidoId.toString(), clienteId.toString(), valor.valor(),
                transacaoId, agora));
    }

    public void recusar(String motivo, Instant agora) {
        transicionar(StatusPagamento.RECUSADO, agora);
        this.motivoRecusa = motivo;
        registrar(new PagamentoRecusado(id.toString(), pedidoId.toString(), clienteId.toString(), valor.valor(),
                transacaoId, motivo, agora));
    }

    /** Pix não pago a tempo. Só vale para Pix pendente cujo prazo já passou; sai como PagamentoRecusado. */
    public void expirar(Instant agora) {
        if (metodo != MetodoPagamento.PIX) {
            throw new RegraDeNegocioException("só pagamento Pix expira");
        }
        if (status == StatusPagamento.PENDENTE && agora.isBefore(pixExpiraEm)) {
            throw new RegraDeNegocioException("o Pix ainda está dentro do prazo de validade");
        }
        transicionar(StatusPagamento.EXPIRADO, agora);
        this.motivoRecusa = "Pix expirado";
        registrar(new PagamentoRecusado(id.toString(), pedidoId.toString(), clienteId.toString(), valor.valor(),
                transacaoId, motivoRecusa, agora));
    }

    public void estornar(Instant agora) {
        transicionar(StatusPagamento.ESTORNADO, agora);
        registrar(new PagamentoEstornado(id.toString(), pedidoId.toString(), clienteId.toString(), valor.valor(),
                transacaoId, agora));
    }

    private void transicionar(StatusPagamento destino, Instant agora) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoDeStatusInvalida(status, destino);
        }
        status = destino;
        atualizadoEm = agora;
    }

    /** O Pix pendente já passou do prazo (candidato a {@link #expirar}). */
    public boolean pixVencido(Instant agora) {
        return metodo == MetodoPagamento.PIX && status == StatusPagamento.PENDENTE && !agora.isBefore(pixExpiraEm);
    }

    public PagamentoId id() {
        return id;
    }

    public PedidoId pedidoId() {
        return pedidoId;
    }

    public ClienteId clienteId() {
        return clienteId;
    }

    public MetodoPagamento metodo() {
        return metodo;
    }

    public Dinheiro valor() {
        return valor;
    }

    public StatusPagamento status() {
        return status;
    }

    public String transacaoId() {
        return transacaoId;
    }

    public String pixCodigo() {
        return pixCodigo;
    }

    public Instant pixExpiraEm() {
        return pixExpiraEm;
    }

    public String motivoRecusa() {
        return motivoRecusa;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
