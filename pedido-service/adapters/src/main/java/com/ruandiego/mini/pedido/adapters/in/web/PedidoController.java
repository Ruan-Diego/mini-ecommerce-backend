package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.in.CancelarPedidoUseCase;
import com.ruandiego.mini.pedido.application.port.in.ConsultarPedidosUseCase;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase.Comando;
import com.ruandiego.mini.pedido.application.port.in.CriarPedidoUseCase.ItemSolicitado;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
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
 * REST dos pedidos do cliente logado. É um adapter de entrada: traduz HTTP para chamadas dos ports de
 * entrada e devolve o resultado. O cliente é sempre o `Principal` da requisição (o `sub` do JWT, já
 * validado pelo gateway e pelo próprio serviço), e nunca algo que o corpo da requisição informe.
 */
@RestController
class PedidoController {

    private final CriarPedidoUseCase criar;
    private final ConsultarPedidosUseCase consultar;
    private final CancelarPedidoUseCase cancelar;

    PedidoController(CriarPedidoUseCase criar, ConsultarPedidosUseCase consultar, CancelarPedidoUseCase cancelar) {
        this.criar = criar;
        this.consultar = consultar;
        this.cancelar = cancelar;
    }

    @PostMapping("/pedidos")
    ResponseEntity<PedidoResposta> criar(Principal principal, @Valid @RequestBody PedidoRequisicao corpo) {
        EnderecoRequisicao e = corpo.endereco();
        List<ItemSolicitado> itens = corpo.itens().stream()
                .map(i -> new ItemSolicitado(new ProdutoId(i.produtoId()), i.quantidade(), new Dinheiro(i.precoVisto())))
                .toList();
        EnderecoEntrega endereco = new EnderecoEntrega(e.destinatario(), e.cep(), e.logradouro(), e.numero(),
                e.complemento(), e.bairro(), e.cidade(), e.uf());

        Pedido pedido = criar.criar(new Comando(cliente(principal), itens, endereco, corpo.freteCodigo()));

        return ResponseEntity.created(URI.create("/pedidos/" + pedido.id())).body(PedidoResposta.de(pedido));
    }

    @GetMapping("/pedidos/meus")
    List<PedidoResposta> meus(Principal principal) {
        return consultar.meusPedidos(cliente(principal)).stream().map(PedidoResposta::de).toList();
    }

    @GetMapping("/pedidos/{id}")
    PedidoResposta obter(Principal principal, @PathVariable String id) {
        return PedidoResposta.de(consultar.obter(cliente(principal), pedidoId(id)));
    }

    @PostMapping("/pedidos/{id}/cancelar")
    PedidoResposta cancelar(Principal principal, @PathVariable String id) {
        return PedidoResposta.de(cancelar.cancelar(cliente(principal), pedidoId(id)));
    }

    private static ClienteId cliente(Principal principal) {
        if (principal == null) {
            throw new NaoAutenticadoException();
        }
        return new ClienteId(principal.getName());
    }

    private static PedidoId pedidoId(String texto) {
        try {
            return new PedidoId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("id de pedido inválido: " + texto);
        }
    }
}
