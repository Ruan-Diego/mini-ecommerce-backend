package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Contrato da mensagem `catalogo.preco-alterado`, como o carrinho a espera (JSON plano, só texto e número):
 * <pre>
 * {"produtoId": "uuid", "sku": "ESF-01", "precoAnterior": 289.90, "precoNovo": 249.90,
 *  "ocorridoEm": "2026-11-19T12:00:00.123456Z"}
 * </pre>
 * `ocorridoEm` é o instante em que o catálogo mudou o preço (ISO-8601 em UTC): é a chave de ordem e de
 * duplicata do consumo. Campos desconhecidos são ignorados, para o catálogo poder acrescentar campos sem
 * quebrar o carrinho. Este formato próprio (e não o record do domínio do catálogo) mantém os dois serviços
 * desacoplados: quem publica, na Fase 3, só precisa respeitar este JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrecoAlteradoMensagem(String produtoId, String sku, BigDecimal precoAnterior, BigDecimal precoNovo,
                                    String ocorridoEm) {
}
