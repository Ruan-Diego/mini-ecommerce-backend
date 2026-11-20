package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.port.in.AdicionarItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AlterarQuantidadeUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AplicarCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.IniciarCheckoutUseCase;
import com.ruandiego.mini.carrinho.application.port.in.ObterCarrinhoUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverItemUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ruandiego.mini.carrinho.adapters.in.web.IdentificacaoDaRequisicao.HEADER_SESSAO;

/**
 * O carrinho "atual" é o de quem pergunta: o do visitante (header X-Sessao-Id) ou o do cliente logado
 * (o `sub` do JWT, via Principal). Mandando os dois, os carrinhos são unidos no primeiro acesso depois do login.
 * O controller só traduz HTTP: identifica quem pede, chama o use case e formata a resposta.
 */
@RestController
@RequestMapping("/carrinhos/atual")
@Tag(name = "Carrinho", description = "Itens, cupom e checkout do carrinho atual")
class CarrinhoController {

    private final ObterCarrinhoUseCase obter;
    private final AdicionarItemUseCase adicionar;
    private final AlterarQuantidadeUseCase alterar;
    private final RemoverItemUseCase removerItem;
    private final AplicarCupomUseCase aplicarCupom;
    private final RemoverCupomUseCase removerCupom;
    private final IniciarCheckoutUseCase checkout;

    CarrinhoController(ObterCarrinhoUseCase obter, AdicionarItemUseCase adicionar, AlterarQuantidadeUseCase alterar,
                       RemoverItemUseCase removerItem, AplicarCupomUseCase aplicarCupom,
                       RemoverCupomUseCase removerCupom, IniciarCheckoutUseCase checkout) {
        this.obter = obter;
        this.adicionar = adicionar;
        this.alterar = alterar;
        this.removerItem = removerItem;
        this.aplicarCupom = aplicarCupom;
        this.removerCupom = removerCupom;
        this.checkout = checkout;
    }

    @GetMapping
    @Operation(summary = "Carrinho atual (vazio se ainda não existe)")
    CarrinhoResposta obter(@RequestHeader(name = HEADER_SESSAO, required = false) @Parameter(description = "Id da sessão do visitante") String sessao,
                           Principal principal) {
        return CarrinhoResposta.de(obter.obter(identidade(sessao, principal)));
    }

    @PostMapping("/itens")
    @Operation(summary = "Adiciona um produto (nome e preço vêm do catálogo; soma se já estiver no carrinho)")
    CarrinhoResposta adicionarItem(@RequestHeader(name = HEADER_SESSAO, required = false) String sessao,
                                   Principal principal,
                                   @Valid @RequestBody ItemRequisicao req) {
        return CarrinhoResposta.de(adicionar.adicionar(identidade(sessao, principal),
                IdentificacaoDaRequisicao.produtoId(req.produtoId()), req.quantidade()));
    }

    @PutMapping("/itens/{produtoId}")
    @Operation(summary = "Define a quantidade (1 a 10) de um item do carrinho")
    CarrinhoResposta alterarQuantidade(@RequestHeader(name = HEADER_SESSAO, required = false) String sessao,
                                       Principal principal,
                                       @PathVariable String produtoId,
                                       @Valid @RequestBody QuantidadeRequisicao req) {
        return CarrinhoResposta.de(alterar.alterar(identidade(sessao, principal),
                IdentificacaoDaRequisicao.produtoId(produtoId), req.quantidade()));
    }

    @DeleteMapping("/itens/{produtoId}")
    @Operation(summary = "Remove um item do carrinho")
    CarrinhoResposta removerItem(@RequestHeader(name = HEADER_SESSAO, required = false) String sessao,
                                 Principal principal,
                                 @PathVariable String produtoId) {
        return CarrinhoResposta.de(removerItem.remover(identidade(sessao, principal),
                IdentificacaoDaRequisicao.produtoId(produtoId)));
    }

    @PostMapping("/cupom")
    @Operation(summary = "Aplica um cupom pelo código (um por carrinho)")
    CarrinhoResposta aplicarCupom(@RequestHeader(name = HEADER_SESSAO, required = false) String sessao,
                                  Principal principal,
                                  @Valid @RequestBody CupomRequisicao req) {
        return CarrinhoResposta.de(aplicarCupom.aplicar(identidade(sessao, principal), req.codigo()));
    }

    @DeleteMapping("/cupom")
    @Operation(summary = "Remove o cupom do carrinho")
    CarrinhoResposta removerCupom(@RequestHeader(name = HEADER_SESSAO, required = false) String sessao,
                                  Principal principal) {
        return CarrinhoResposta.de(removerCupom.removerCupom(identidade(sessao, principal)));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Confere o carrinho do cliente logado e devolve a foto final (exige login)")
    CarrinhoResposta checkout(Principal principal) {
        return CarrinhoResposta.de(checkout.iniciar(IdentificacaoDaRequisicao.exigirCliente(principal)));
    }

    private static Identidade identidade(String sessao, Principal principal) {
        return IdentificacaoDaRequisicao.identidade(sessao, principal);
    }
}
