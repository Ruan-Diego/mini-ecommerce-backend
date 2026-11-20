package com.ruandiego.mini.estoque.adapters.in.web;

import com.ruandiego.mini.estoque.application.port.in.AjustarEstoqueUseCase;
import com.ruandiego.mini.estoque.application.port.in.ConsultarEstoqueUseCase;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST do estoque. Adapter de entrada: traduz HTTP para os ports de entrada. A consulta é pública (a vitrine
 * mostra disponibilidade); o ajuste é de administrador (regra no SegurancaConfig e no gateway).
 */
@RestController
class EstoqueController {

    private final ConsultarEstoqueUseCase consultar;
    private final AjustarEstoqueUseCase ajustar;

    EstoqueController(ConsultarEstoqueUseCase consultar, AjustarEstoqueUseCase ajustar) {
        this.consultar = consultar;
        this.ajustar = ajustar;
    }

    @GetMapping("/estoque/{produtoId}")
    EstoqueResposta obter(@PathVariable String produtoId) {
        return EstoqueResposta.de(consultar.consultar(produtoId(produtoId)));
    }

    @PutMapping("/admin/estoque/{produtoId}")
    EstoqueResposta definir(@PathVariable String produtoId, @Valid @RequestBody AjusteRequisicao corpo) {
        ItemEstoque item = ajustar.definirDisponivel(produtoId(produtoId), corpo.disponivel());
        return EstoqueResposta.de(item);
    }

    private static ProdutoId produtoId(String texto) {
        try {
            return ProdutoId.de(texto);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("produtoId inválido: " + texto);
        }
    }
}
