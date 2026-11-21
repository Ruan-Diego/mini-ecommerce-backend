package com.ruandiego.mini.estoque.adapters.in.web;

import com.ruandiego.mini.estoque.application.port.in.ConsultarEstoqueAdminUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultas do admin no estoque. O ajuste (PUT /admin/estoque/{id}) continua no EstoqueController. Quem pode
 * chamar `/admin/**` é decidido pela segurança (papel admin no gateway e no serviço), não por este adapter.
 */
@RestController
@RequestMapping("/admin")
class AdminEstoqueController {

    private final ConsultarEstoqueAdminUseCase consultar;

    AdminEstoqueController(ConsultarEstoqueAdminUseCase consultar) {
        this.consultar = consultar;
    }

    /** `?abaixoDe=5` filtra o estoque baixo; sem o parâmetro, lista tudo. Menor saldo primeiro. */
    @GetMapping("/estoque")
    List<EstoqueResposta> listar(@RequestParam(required = false) Integer abaixoDe) {
        return consultar.listar(abaixoDe).stream().map(EstoqueResposta::de).toList();
    }

    /** Indicador do painel que é do estoque; os de pedidos ficam em /api/pedidos/admin/indicadores. */
    @GetMapping("/indicadores")
    IndicadoresEstoqueResposta indicadores() {
        ConsultarEstoqueAdminUseCase.Indicadores i = consultar.indicadores();
        return new IndicadoresEstoqueResposta(i.estoqueBaixo(), i.limite());
    }

    record IndicadoresEstoqueResposta(long estoqueBaixo, int limite) {
    }
}
