package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarrinhoTest {

    private static final Instant AGORA = Instant.parse("2026-11-19T12:00:00Z");
    private static final Instant FUTURO = AGORA.plusSeconds(86_400);
    private static final ProdutoId ESFERA = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId CUBO = new ProdutoId(UUID.randomUUID());

    private static Carrinho comEsferaECubo() {
        Carrinho c = Carrinho.criar();
        c.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 2);
        c.adicionarItem(CUBO, "Cubo Preto", Dinheiro.de("50.00"), 1);
        return c;
    }

    @Nested
    class Itens {

        @Test
        void carrinhoNovoEstaVazioComSubtotalZero() {
            Carrinho c = Carrinho.criar();

            assertThat(c.vazio()).isTrue();
            assertThat(c.subtotal()).isEqualTo(Dinheiro.ZERO);
        }

        @Test
        void subtotalEhASomaDePrecoVezesQuantidade() {
            assertThat(comEsferaECubo().subtotal()).isEqualTo(Dinheiro.de("250.00"));
        }

        @Test
        void adicionarOMesmoProdutoSomaAsQuantidades() {
            Carrinho c = comEsferaECubo();

            c.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 3);

            assertThat(c.itens()).hasSize(2);
            assertThat(c.itens().get(0).quantidade()).isEqualTo(5);
            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("550.00"));
        }

        @Test
        void somaDeAdicoesNaoPodePassarDeDez() {
            Carrinho c = Carrinho.criar();
            c.adicionarItem(ESFERA, "Esfera", Dinheiro.de("10.00"), 8);

            assertThatThrownBy(() -> c.adicionarItem(ESFERA, "Esfera", Dinheiro.de("10.00"), 3))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThat(c.itens().get(0).quantidade()).isEqualTo(8);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 11, 100})
        void quantidadeForaDeUmADezEhRecusada(int quantidade) {
            Carrinho c = Carrinho.criar();

            assertThatThrownBy(() -> c.adicionarItem(ESFERA, "Esfera", Dinheiro.de("10.00"), quantidade))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("entre 1 e 10");
            assertThat(c.vazio()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10})
        void limitesDaQuantidadeSaoAceitos(int quantidade) {
            Carrinho c = Carrinho.criar();

            c.adicionarItem(ESFERA, "Esfera", Dinheiro.de("10.00"), quantidade);

            assertThat(c.quantidadeTotal()).isEqualTo(quantidade);
        }

        @Test
        void alterarQuantidadeRecalculaOSubtotal() {
            Carrinho c = comEsferaECubo();

            c.alterarQuantidade(ESFERA, 5);

            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("550.00"));
        }

        @Test
        void alterarQuantidadeValidaOLimite() {
            Carrinho c = comEsferaECubo();

            assertThatThrownBy(() -> c.alterarQuantidade(ESFERA, 11)).isInstanceOf(RegraDeNegocioException.class);
            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("250.00"));
        }

        @Test
        void alterarOuRemoverProdutoForaDoCarrinhoEhErro() {
            Carrinho c = Carrinho.criar();

            assertThatThrownBy(() -> c.alterarQuantidade(ESFERA, 2)).isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> c.removerItem(ESFERA)).isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void removerItemRecalculaOSubtotal() {
            Carrinho c = comEsferaECubo();

            c.removerItem(ESFERA);

            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("50.00"));
            assertThat(c.itens()).hasSize(1);
        }

        @Test
        void precoZeroOuNomeVazioSaoRecusados() {
            Carrinho c = Carrinho.criar();

            assertThatThrownBy(() -> c.adicionarItem(ESFERA, "Esfera", Dinheiro.ZERO, 1))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> c.adicionarItem(ESFERA, " ", Dinheiro.de("1.00"), 1))
                    .isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void precoAlteradoNoCatalogoAtualizaOItemEOSubtotal() {
            Carrinho c = comEsferaECubo();

            c.atualizarPreco(ESFERA, Dinheiro.de("120.00"));

            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("290.00"));
        }

        @Test
        void precoAlteradoDeProdutoForaDoCarrinhoEhIgnorado() {
            Carrinho c = Carrinho.criar();

            c.atualizarPreco(ESFERA, Dinheiro.de("120.00"));

            assertThat(c.vazio()).isTrue();
        }

        @Test
        void listaDeItensNaoPermiteAlteracaoPorFora() {
            Carrinho c = comEsferaECubo();

            assertThatThrownBy(() -> c.itens().clear()).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void esvaziarLimpaItensECupom() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("BEMVINDO", 10, FUTURO, null), AGORA);

            c.esvaziar();

            assertThat(c.vazio()).isTrue();
            assertThat(c.cupom()).isNull();
            assertThat(c.subtotal()).isEqualTo(Dinheiro.ZERO);
        }
    }

    @Nested
    class Cupons {

        @Test
        void cupomPercentualDescontaSobreOSubtotal() {
            Carrinho c = comEsferaECubo();

            c.aplicarCupom(Cupom.percentual("DEZ", 10, FUTURO, null), AGORA);

            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.de("25.00"));
            assertThat(c.total(AGORA)).isEqualTo(Dinheiro.de("225.00"));
        }

        @Test
        void cupomDeValorFixoDescontaOValor() {
            Carrinho c = comEsferaECubo();

            c.aplicarCupom(Cupom.valorFixo("VINTE", Dinheiro.de("20.00"), FUTURO, null), AGORA);

            assertThat(c.total(AGORA)).isEqualTo(Dinheiro.de("230.00"));
        }

        @Test
        void descontoNuncaPassaDoSubtotal() {
            Carrinho c = Carrinho.criar();
            c.adicionarItem(CUBO, "Cubo", Dinheiro.de("30.00"), 1);

            c.aplicarCupom(Cupom.valorFixo("GRANDE", Dinheiro.de("100.00"), FUTURO, null), AGORA);

            assertThat(c.total(AGORA)).isEqualTo(Dinheiro.ZERO);
        }

        @Test
        void percentualArredondaParaCentavos() {
            Carrinho c = Carrinho.criar();
            c.adicionarItem(CUBO, "Cubo", Dinheiro.de("33.33"), 1);

            c.aplicarCupom(Cupom.percentual("DEZ", 10, FUTURO, null), AGORA);

            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.de("3.33"));
        }

        @Test
        void descontoAcompanhaASomaQuandoOsItensMudam() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("DEZ", 10, FUTURO, null), AGORA);

            c.alterarQuantidade(ESFERA, 4);

            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.de("45.00"));
        }

        @Test
        void cupomVencidoEhRecusado() {
            Carrinho c = comEsferaECubo();

            assertThatThrownBy(() -> c.aplicarCupom(Cupom.percentual("VELHO", 10, AGORA.minusSeconds(1), null), AGORA))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("vencido");
            assertThat(c.cupom()).isNull();
        }

        @Test
        void cupomValeAteOInstanteDaValidadeInclusive() {
            Carrinho c = comEsferaECubo();

            c.aplicarCupom(Cupom.percentual("HOJE", 10, AGORA, null), AGORA);

            assertThat(c.cupom()).isNotNull();
        }

        @Test
        void subtotalAbaixoDoMinimoRecusaOCupom() {
            Carrinho c = comEsferaECubo();

            assertThatThrownBy(() ->
                    c.aplicarCupom(Cupom.percentual("MIN", 10, FUTURO, Dinheiro.de("250.01")), AGORA))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("mínimo");
        }

        @Test
        void subtotalIgualAoMinimoAceitaOCupom() {
            Carrinho c = comEsferaECubo();

            c.aplicarCupom(Cupom.percentual("MIN", 10, FUTURO, Dinheiro.de("250.00")), AGORA);

            assertThat(c.cupom().codigo()).isEqualTo("MIN");
        }

        @Test
        void soHaUmCupomPorCarrinho() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("UM", 10, FUTURO, null), AGORA);

            assertThatThrownBy(() -> c.aplicarCupom(Cupom.percentual("DOIS", 20, FUTURO, null), AGORA))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("UM");
            assertThat(c.cupom().codigo()).isEqualTo("UM");
        }

        @Test
        void removerOCupomPermiteAplicarOutro() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("UM", 10, FUTURO, null), AGORA);

            c.removerCupom();
            c.aplicarCupom(Cupom.percentual("DOIS", 20, FUTURO, null), AGORA);

            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.de("50.00"));
        }

        @Test
        void subtotalQueCaiAbaixoDoMinimoZeraODescontoMasMantemOCupom() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("MIN", 10, FUTURO, Dinheiro.de("200.00")), AGORA);

            c.removerItem(ESFERA);

            assertThat(c.cupom()).isNotNull();
            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.ZERO);

            c.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 2);

            assertThat(c.desconto(AGORA)).isEqualTo(Dinheiro.de("25.00"));
        }

        @Test
        void cupomQueVenceDepoisDeAplicadoNaoDescontaMais() {
            Carrinho c = comEsferaECubo();
            c.aplicarCupom(Cupom.percentual("DEZ", 10, FUTURO, null), AGORA);

            assertThat(c.desconto(FUTURO.plusSeconds(1))).isEqualTo(Dinheiro.ZERO);
        }

        @Test
        void semCupomTotalEhOSubtotal() {
            Carrinho c = comEsferaECubo();

            assertThat(c.total(AGORA)).isEqualTo(c.subtotal());
        }

        @Test
        void codigoDoCupomEhNormalizado() {
            assertThat(Cupom.percentual("  bemvindo ", 10, FUTURO, null).codigo()).isEqualTo("BEMVINDO");
        }

        @Test
        void cupomInvalidoNaoEhCriado() {
            assertThatThrownBy(() -> Cupom.percentual("X", 0, FUTURO, null)).isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Cupom.percentual("X", 101, FUTURO, null)).isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Cupom.valorFixo("X", Dinheiro.ZERO, FUTURO, null))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Cupom.percentual(" ", 10, FUTURO, null)).isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Nested
    class Uniao {

        private Carrinho visitante() {
            Carrinho v = Carrinho.criar();
            v.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("90.00"), 3);
            return v;
        }

        @Test
        void itemSoDoVisitanteEntraNoCarrinhoDoCliente() {
            Carrinho cliente = Carrinho.criar();
            cliente.adicionarItem(CUBO, "Cubo Preto", Dinheiro.de("50.00"), 1);

            cliente.unirCom(visitante(), AGORA);

            assertThat(cliente.itens()).hasSize(2);
            assertThat(cliente.subtotal()).isEqualTo(Dinheiro.de("320.00"));
        }

        @Test
        void produtoNosDoisSomaQuantidadesEMantemOPrecoDoCliente() {
            Carrinho cliente = Carrinho.criar();
            cliente.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 2);

            cliente.unirCom(visitante(), AGORA);

            assertThat(cliente.itens()).hasSize(1);
            assertThat(cliente.itens().get(0).quantidade()).isEqualTo(5);
            assertThat(cliente.itens().get(0).precoUnitario()).isEqualTo(Dinheiro.de("100.00"));
        }

        @Test
        void somaAcimaDeDezEhCortadaNoTetoSemFalhar() {
            Carrinho cliente = Carrinho.criar();
            cliente.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 9);

            cliente.unirCom(visitante(), AGORA);

            assertThat(cliente.itens().get(0).quantidade()).isEqualTo(10);
        }

        @Test
        void cupomDoClientePrevalece() {
            Carrinho cliente = Carrinho.criar();
            cliente.adicionarItem(CUBO, "Cubo Preto", Dinheiro.de("50.00"), 1);
            cliente.aplicarCupom(Cupom.percentual("CLIENTE", 5, FUTURO, null), AGORA);
            Carrinho v = visitante();
            v.aplicarCupom(Cupom.percentual("VISITANTE", 50, FUTURO, null), AGORA);

            cliente.unirCom(v, AGORA);

            assertThat(cliente.cupom().codigo()).isEqualTo("CLIENTE");
        }

        @Test
        void semCupomNoClienteAdotaOCupomDoVisitante() {
            Carrinho cliente = Carrinho.criar();
            Carrinho v = visitante();
            v.aplicarCupom(Cupom.percentual("VISITANTE", 10, FUTURO, null), AGORA);

            cliente.unirCom(v, AGORA);

            assertThat(cliente.cupom().codigo()).isEqualTo("VISITANTE");
        }

        @Test
        void cupomDoVisitanteVencidoNaoEhAdotado() {
            Carrinho cliente = Carrinho.criar();
            Carrinho v = visitante();
            v.aplicarCupom(Cupom.percentual("VISITANTE", 10, AGORA.plusSeconds(10), null), AGORA);

            cliente.unirCom(v, AGORA.plusSeconds(11));

            assertThat(cliente.cupom()).isNull();
        }

        @Test
        void unirComOProprioCarrinhoNaoFazNada() {
            Carrinho c = comEsferaECubo();

            c.unirCom(c, AGORA);

            assertThat(c.subtotal()).isEqualTo(Dinheiro.de("250.00"));
        }
    }

    @Test
    void reconstituirRecalculaOSubtotalEMantemOCupom() {
        Carrinho original = comEsferaECubo();
        Cupom cupom = Cupom.percentual("DEZ", 10, FUTURO, null);

        Carrinho volta = Carrinho.reconstituir(original.id(), original.itens(), cupom);

        assertThat(volta.subtotal()).isEqualTo(Dinheiro.de("250.00"));
        assertThat(volta.cupom()).isEqualTo(cupom);
        assertThat(volta).isEqualTo(original);
        assertThat(volta.eventosPendentes()).isEmpty();
    }
}
