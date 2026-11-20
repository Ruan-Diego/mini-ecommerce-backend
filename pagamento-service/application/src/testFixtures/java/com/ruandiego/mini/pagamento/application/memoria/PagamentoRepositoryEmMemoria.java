package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter em memória do PagamentoRepository. Guarda e devolve cópias (reconstituídas), como um banco faria:
 * mudar o objeto que o chamador tem na mão não altera o que foi salvo até chamar {@code salvar} de novo.
 */
public class PagamentoRepositoryEmMemoria implements PagamentoRepository {

    private final Map<PagamentoId, Pagamento> dados = new HashMap<>();

    @Override
    public void salvar(Pagamento pagamento) {
        dados.put(pagamento.id(), copia(pagamento));
    }

    @Override
    public Optional<Pagamento> buscarPorId(PagamentoId id) {
        return Optional.ofNullable(dados.get(id)).map(PagamentoRepositoryEmMemoria::copia);
    }

    @Override
    public Optional<Pagamento> buscarPorTransacao(String transacaoId) {
        return dados.values().stream().filter(p -> transacaoId.equals(p.transacaoId())).findFirst()
                .map(PagamentoRepositoryEmMemoria::copia);
    }

    @Override
    public List<Pagamento> listarPorPedido(PedidoId pedido) {
        return dados.values().stream()
                .filter(p -> p.pedidoId().equals(pedido))
                .sorted(Comparator.comparing(Pagamento::criadoEm).reversed())
                .map(PagamentoRepositoryEmMemoria::copia)
                .toList();
    }

    @Override
    public List<Pagamento> listarPixVencidos(Instant agora, int limite) {
        return dados.values().stream()
                .filter(p -> p.pixVencido(agora))
                .sorted(Comparator.comparing(Pagamento::pixExpiraEm))
                .limit(limite)
                .map(PagamentoRepositoryEmMemoria::copia)
                .toList();
    }

    public void limpar() {
        dados.clear();
    }

    private static Pagamento copia(Pagamento p) {
        return Pagamento.reconstituir(p.id(), p.pedidoId(), p.clienteId(), p.metodo(), p.valor(), p.status(),
                p.transacaoId(), p.pixCodigo(), p.pixExpiraEm(), p.motivoRecusa(), p.criadoEm(), p.atualizadoEm());
    }
}
