package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase;
import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase.Filtro;
import com.ruandiego.mini.pedido.application.port.in.ConsultarIndicadoresUseCase;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * REST do admin sobre pedidos de qualquer cliente. Adapter de entrada como o PedidoController, mas sem dono:
 * quem pode chamar é decidido pela segurança (`/admin/**` exige o papel admin, no gateway e no próprio serviço).
 * Aqui só se traduz HTTP para os ports; regras de transição e de rastreio são do domínio.
 */
@RestController
@RequestMapping("/admin")
class AdminPedidosController {

    private final AdministrarPedidosUseCase administrar;
    private final ConsultarIndicadoresUseCase indicadores;

    AdminPedidosController(AdministrarPedidosUseCase administrar, ConsultarIndicadoresUseCase indicadores) {
        this.administrar = administrar;
        this.indicadores = indicadores;
    }

    @GetMapping("/indicadores")
    IndicadoresResposta indicadores() {
        return IndicadoresResposta.de(indicadores.indicadores());
    }

    /** `status` pode repetir (`?status=PAGO&status=ENVIADO`); `de` e `ate` são datas ISO (aaaa-mm-dd), inclusivas. */
    @GetMapping("/pedidos")
    PaginaResposta<PedidoAdminResposta> listar(@RequestParam(required = false) List<String> status,
                                               @RequestParam(required = false) String de,
                                               @RequestParam(required = false) String ate,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanho) {
        Filtro filtro = new Filtro(statusDe(status), data("de", de), data("ate", ate));
        return PaginaResposta.de(administrar.listar(filtro, pagina, tamanho), PedidoAdminResposta::de);
    }

    @GetMapping("/pedidos/{id}")
    PedidoAdminResposta obter(@PathVariable String id) {
        return PedidoAdminResposta.de(administrar.obter(pedidoId(id)));
    }

    @PatchMapping("/pedidos/{id}/status")
    PedidoAdminResposta alterarStatus(@PathVariable String id, @Valid @RequestBody AlterarStatusRequisicao corpo) {
        return PedidoAdminResposta.de(administrar.alterarStatus(pedidoId(id), status(corpo.status()),
                corpo.codigoRastreio()));
    }

    private static Set<StatusPedido> statusDe(List<String> textos) {
        Set<StatusPedido> status = EnumSet.noneOf(StatusPedido.class);
        if (textos != null) {
            textos.stream().flatMap(t -> List.of(t.split(",")).stream()).filter(t -> !t.isBlank())
                    .map(AdminPedidosController::status).forEach(status::add);
        }
        return status;
    }

    private static StatusPedido status(String texto) {
        try {
            return StatusPedido.valueOf(texto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("status desconhecido: " + texto);
        }
    }

    private static LocalDate data(String nome, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new RequisicaoInvalidaException("'" + nome + "' deve ser uma data aaaa-mm-dd: " + texto);
        }
    }

    private static PedidoId pedidoId(String texto) {
        try {
            return new PedidoId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("id de pedido inválido: " + texto);
        }
    }
}
