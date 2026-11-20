package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.in.ConsultarPagamentosUseCase;
import com.ruandiego.mini.pagamento.application.port.in.EstornarPagamentoUseCase;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase.Comando;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST dos pagamentos do cliente logado. É um adapter de entrada: traduz HTTP para chamadas dos ports de
 * entrada. O cliente é sempre o `Principal` (o `sub` do JWT), nunca algo que o corpo informe.
 * O estorno é de administrador: a regra `hasRole("admin")` fica no SegurancaConfig, aqui não há Principal de cliente.
 */
@RestController
class PagamentoController {

    private final IniciarPagamentoUseCase iniciar;
    private final ConsultarPagamentosUseCase consultar;
    private final EstornarPagamentoUseCase estornar;

    PagamentoController(IniciarPagamentoUseCase iniciar, ConsultarPagamentosUseCase consultar,
                        EstornarPagamentoUseCase estornar) {
        this.iniciar = iniciar;
        this.consultar = consultar;
        this.estornar = estornar;
    }

    /** Cartão recusado não é erro HTTP: o pagamento é criado e a resposta traz `status: RECUSADO`. */
    @PostMapping("/pagamentos")
    ResponseEntity<PagamentoResposta> iniciar(Principal principal, @Valid @RequestBody PagamentoRequisicao corpo) {
        Pagamento pagamento = iniciar.iniciar(new Comando(cliente(principal), new PedidoId(corpo.pedidoId()),
                new Dinheiro(corpo.valor()), corpo.metodo(), corpo.numeroCartao()));
        return ResponseEntity.created(URI.create("/pagamentos/" + pagamento.id())).body(PagamentoResposta.de(pagamento));
    }

    @GetMapping("/pagamentos/{id}")
    PagamentoResposta obter(Principal principal, @PathVariable String id) {
        return PagamentoResposta.de(consultar.obter(cliente(principal), pagamentoId(id)));
    }

    @GetMapping("/pagamentos/pedido/{pedidoId}")
    List<PagamentoResposta> doPedido(Principal principal, @PathVariable String pedidoId) {
        return consultar.doPedido(cliente(principal), pedidoId(pedidoId)).stream().map(PagamentoResposta::de).toList();
    }

    @PostMapping("/pagamentos/{id}/estorno")
    PagamentoResposta estornar(@PathVariable String id) {
        return PagamentoResposta.de(estornar.estornar(pagamentoId(id)));
    }

    private static ClienteId cliente(Principal principal) {
        if (principal == null) {
            throw new NaoAutenticadoException();
        }
        return new ClienteId(principal.getName());
    }

    private static PagamentoId pagamentoId(String texto) {
        return new PagamentoId(uuid(texto, "pagamento"));
    }

    private static PedidoId pedidoId(String texto) {
        return new PedidoId(uuid(texto, "pedido"));
    }

    private static UUID uuid(String texto, String recurso) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("id de " + recurso + " inválido: " + texto);
        }
    }
}
