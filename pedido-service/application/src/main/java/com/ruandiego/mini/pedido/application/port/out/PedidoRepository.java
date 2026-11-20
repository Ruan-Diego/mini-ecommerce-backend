package com.ruandiego.mini.pedido.application.port.out;

import com.ruandiego.mini.pedido.application.Pagina;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistência do agregado Pedido. Salvar cria ou atualiza (o status muda com o tempo). */
public interface PedidoRepository {

    void salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(PedidoId id);

    /** Do mais recente para o mais antigo. */
    List<Pedido> listarPorCliente(ClienteId cliente);

    /**
     * Pedidos ainda AGUARDANDO_PAGAMENTO criados em `criadosAte` ou antes (limite inclusivo), do mais antigo para
     * o mais novo, no máximo `quantidade`. É o que o job de expiração cancela.
     */
    List<Pedido> listarAguardandoPagamentoCriadosAte(Instant criadosAte, int quantidade);

    /** Pedidos de qualquer cliente que atendem aos critérios, do mais recente para o mais antigo. */
    Pagina<Pedido> buscar(Criterios criterios, int pagina, int tamanho);

    long contar(Criterios criterios);

    /**
     * Filtro da busca do admin. `status` vazio = qualquer status. O período é `[criadosDesde, criadosAntes)`:
     * início inclusivo, fim exclusivo; nulo = sem limite daquele lado.
     */
    record Criterios(Set<StatusPedido> status, Instant criadosDesde, Instant criadosAntes) {

        public Criterios {
            status = status == null ? Set.of() : Set.copyOf(status);
        }

        public static Criterios todos() {
            return new Criterios(Set.of(), null, null);
        }

        public boolean atende(Pedido pedido) {
            return (status.isEmpty() || status.contains(pedido.status()))
                    && (criadosDesde == null || !pedido.criadoEm().isBefore(criadosDesde))
                    && (criadosAntes == null || pedido.criadoEm().isBefore(criadosAntes));
        }
    }
}
