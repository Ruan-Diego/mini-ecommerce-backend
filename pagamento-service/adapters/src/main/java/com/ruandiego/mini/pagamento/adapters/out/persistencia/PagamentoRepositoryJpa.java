package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adapter real do PagamentoRepository: MySQL via JPA. A transação é a do use case (aberta no wiring).
 * Entidade de domínio e entidade JPA são coisas diferentes: o mapeamento é feito aqui, à mão.
 */
@Component
public class PagamentoRepositoryJpa implements PagamentoRepository {

    private final PagamentoJpaRepository jpa;

    PagamentoRepositoryJpa(PagamentoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Pagamento pagamento) {
        // Carrega a linha existente (se houver) para o Hibernate gerar só o UPDATE do que mudou.
        PagamentoJpa linha = jpa.findById(pagamento.id().toString())
                .orElseGet(() -> new PagamentoJpa(pagamento.id().toString()));
        linha.preencher(pagamento.pedidoId().toString(), pagamento.clienteId().valor(), pagamento.metodo(),
                pagamento.valor().valor(), pagamento.status(), pagamento.transacaoId(), pagamento.pixCodigo(),
                pagamento.pixExpiraEm(), pagamento.motivoRecusa(), pagamento.criadoEm(), pagamento.atualizadoEm());
        jpa.save(linha);
    }

    @Override
    public Optional<Pagamento> buscarPorId(PagamentoId id) {
        return jpa.findById(id.toString()).map(PagamentoRepositoryJpa::paraDominio);
    }

    @Override
    public Optional<Pagamento> buscarPorTransacao(String transacaoId) {
        return jpa.findByTransacaoId(transacaoId).map(PagamentoRepositoryJpa::paraDominio);
    }

    @Override
    public List<Pagamento> listarPorPedido(PedidoId pedido) {
        return jpa.findByPedidoIdOrderByCriadoEmDesc(pedido.toString()).stream()
                .map(PagamentoRepositoryJpa::paraDominio).toList();
    }

    @Override
    public List<Pagamento> listarPixVencidos(Instant agora, int limite) {
        return jpa.pixVencidos(agora, PageRequest.of(0, limite)).stream()
                .map(PagamentoRepositoryJpa::paraDominio).toList();
    }

    private static Pagamento paraDominio(PagamentoJpa p) {
        return Pagamento.reconstituir(PagamentoId.de(p.getId()), PedidoId.de(p.getPedidoId()),
                new ClienteId(p.getClienteId()), p.getMetodo(), new Dinheiro(p.getValor()), p.getStatus(),
                p.getTransacaoId(), p.getPixCodigo(), p.getPixExpiraEm(), p.getMotivoRecusa(), p.getCriadoEm(),
                p.getAtualizadoEm());
    }
}
