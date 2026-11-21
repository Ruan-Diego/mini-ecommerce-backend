package com.ruandiego.mini.catalogo.adapters.out.armazenamento;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.application.port.out.ArmazenamentoImagens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Adapter real do ArmazenamentoImagens: grava no disco local, com nome aleatório (UUID + extensão do tipo
 * detectado), então o nome enviado pelo navegador nunca vira caminho de arquivo. Bom para desenvolvimento e
 * para uma instância só; com várias réplicas, troque por um adapter de S3/MinIO sem mexer no resto.
 */
@Component
public class ArmazenamentoImagensEmDisco implements ArmazenamentoImagens {

    private final Path diretorio;
    private final String urlPublica;

    ArmazenamentoImagensEmDisco(@Value("${catalogo.imagens.diretorio:./dados/imagens}") String diretorio,
                                @Value("${catalogo.imagens.url-publica}") String urlPublica) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    @Override
    public String guardar(ArquivoImagem arquivo) {
        String nome = UUID.randomUUID() + "." + arquivo.tipo().extensao();
        try {
            Files.createDirectories(diretorio);
            // CREATE_NEW: se por acaso o nome já existir, falha em vez de sobrescrever a imagem de outro produto.
            Files.write(diretorio.resolve(nome), arquivo.conteudo(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("não foi possível gravar a imagem em " + diretorio, e);
        }
        return urlPublica + "/" + nome;
    }

    Path diretorio() {
        return diretorio;
    }
}
