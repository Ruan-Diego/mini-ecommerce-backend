package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter em memória do PedidoRepository. Guarda e devolve cópias (reconstituídas), como um banco faria:
 * mudar o objeto que o chamador tem na mão não altera o que foi salvo até chamar {@code salvar} de novo.
 */
public class PedidoRepositoryEmMemoria implements PedidoRepository {

    private final Map<PedidoId, Pedido> dados = new HashMap<>();

    @Override
    public void salvar(Pedido pedido) {
        dados.put(pedido.id(), copia(pedido));
    }

    @Override
    public Optional<Pedido> buscarPorId(PedidoId id) {
        return Optional.ofNullable(dados.get(id)).map(PedidoRepositoryEmMemoria::copia);
    }

    @Override
    public List<Pedido> listarPorCliente(ClienteId cliente) {
        return dados.values().stream()
                .filter(pedido -> pedido.clienteId().equals(cliente))
                .sorted(Comparator.comparing(Pedido::criadoEm).reversed())
                .map(PedidoRepositoryEmMemoria::copia)
                .toList();
    }

    public void limpar() {
        dados.clear();
    }

    private static Pedido copia(Pedido p) {
        return Pedido.reconstituir(p.id(), p.clienteId(), p.itens(), p.endereco(), p.frete(), p.status(),
                p.criadoEm(), p.atualizadoEm());
    }
}
