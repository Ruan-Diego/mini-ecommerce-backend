package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.ServicoIndisponivelException;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway programável para testes: aprova ou recusa o cartão conforme {@code aprovarCartoes}, lembra o que
 * recebeu e pode "cair". Não é o GatewayFakeAdapter (que decide pelo último dígito do cartão).
 */
public class GatewayPagamentoEmMemoria implements GatewayPagamento {

    private final AtomicInteger sequencia = new AtomicInteger();
    private final List<String> cartoesCobrados = new ArrayList<>();
    private final List<String> estornos = new ArrayList<>();
    private boolean aprovarCartoes = true;
    private boolean foraDoAr;

    public void aprovarCartoes(boolean aprovar) {
        this.aprovarCartoes = aprovar;
    }

    public void ficarForaDoAr(boolean foraDoAr) {
        this.foraDoAr = foraDoAr;
    }

    public List<String> cartoesCobrados() {
        return cartoesCobrados;
    }

    public List<String> estornos() {
        return estornos;
    }

    @Override
    public ResultadoCartao cobrarCartao(Dinheiro valor, String numeroCartao) {
        verificarDisponibilidade();
        cartoesCobrados.add(numeroCartao);
        String transacao = "tx-" + sequencia.incrementAndGet();
        return aprovarCartoes
                ? new ResultadoCartao(transacao, true, null)
                : new ResultadoCartao(transacao, false, "cartão recusado");
    }

    @Override
    public CobrancaPix gerarPix(Dinheiro valor, Instant expiraEm) {
        verificarDisponibilidade();
        String transacao = "tx-" + sequencia.incrementAndGet();
        return new CobrancaPix(transacao, "PIX-" + transacao);
    }

    @Override
    public void estornar(String transacaoId) {
        verificarDisponibilidade();
        estornos.add(transacaoId);
    }

    private void verificarDisponibilidade() {
        if (foraDoAr) {
            throw new ServicoIndisponivelException("gateway indisponível", null);
        }
    }
}
