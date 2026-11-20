package com.ruandiego.mini.catalogo.application;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArquivoImagemTest {

    private static byte[] comecando(int tamanho, int... inicio) {
        byte[] b = new byte[tamanho];
        for (int i = 0; i < inicio.length; i++) {
            b[i] = (byte) inicio[i];
        }
        return b;
    }

    @Test
    void reconheceJpegPngEWebpPelosPrimeirosBytes() {
        assertThat(ArquivoImagem.de(comecando(10, 0xFF, 0xD8, 0xFF, 0xE0)).tipo()).isEqualTo(ArquivoImagem.Tipo.JPEG);
        assertThat(ArquivoImagem.de(comecando(10, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)).tipo())
                .isEqualTo(ArquivoImagem.Tipo.PNG);
        assertThat(ArquivoImagem.de(comecando(16, 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P')).tipo())
                .isEqualTo(ArquivoImagem.Tipo.WEBP);
    }

    @Test
    void recusaOQueNaoEhImagemMesmoComNomeDeImagem() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] riffSemWebp = comecando(16, 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E');

        assertThatThrownBy(() -> ArquivoImagem.de(html)).isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("JPEG, PNG ou WebP");
        assertThatThrownBy(() -> ArquivoImagem.de(riffSemWebp)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> ArquivoImagem.de(new byte[]{(byte) 0xFF})).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaVazioEMaiorQueDoisMega() {
        assertThatThrownBy(() -> ArquivoImagem.de(new byte[0])).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> ArquivoImagem.de(null)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> ArquivoImagem.de(comecando(ArquivoImagem.TAMANHO_MAXIMO + 1, 0xFF, 0xD8, 0xFF)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("2 MB");
        assertThat(ArquivoImagem.de(comecando(ArquivoImagem.TAMANHO_MAXIMO, 0xFF, 0xD8, 0xFF)).tamanho())
                .isEqualTo(ArquivoImagem.TAMANHO_MAXIMO);
    }

    @Test
    void conteudoEhUmaCopia() {
        byte[] original = comecando(10, 0xFF, 0xD8, 0xFF);
        ArquivoImagem arquivo = ArquivoImagem.de(original);

        original[5] = 42;
        arquivo.conteudo()[6] = 42;

        assertThat(arquivo.conteudo()[5]).isZero();
        assertThat(arquivo.conteudo()[6]).isZero();
    }
}
