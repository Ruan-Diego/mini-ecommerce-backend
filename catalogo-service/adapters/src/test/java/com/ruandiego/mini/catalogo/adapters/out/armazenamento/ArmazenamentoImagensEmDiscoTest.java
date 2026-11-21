package com.ruandiego.mini.catalogo.adapters.out.armazenamento;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArmazenamentoImagensEmDiscoTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @TempDir
    Path pasta;

    @Test
    void gravaComNomeAleatorioEDevolveAUrlPublica() throws Exception {
        Path destino = pasta.resolve("imagens"); // ainda não existe: o adapter cria
        ArmazenamentoImagensEmDisco disco = new ArmazenamentoImagensEmDisco(destino.toString(),
                "http://localhost:8080/api/catalogo/imagens/");

        String url = disco.guardar(ArquivoImagem.de(JPEG));
        String outra = disco.guardar(ArquivoImagem.de(JPEG));

        assertThat(url).matches("http://localhost:8080/api/catalogo/imagens/[0-9a-f-]{36}\\.jpg");
        assertThat(outra).isNotEqualTo(url);
        Path arquivo = destino.resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(Files.readAllBytes(arquivo)).isEqualTo(JPEG);
        try (var arquivos = Files.list(destino)) {
            assertThat(arquivos).hasSize(2);
        }
    }
}
