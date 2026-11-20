package com.ruandiego.mini.catalogo.adapters.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Serve os arquivos do upload em GET /imagens/{arquivo}. O ResourceHandler do Spring já recusa `..` e
 * caminhos fora do diretório, e o Content-Type sai da extensão (que o adapter escolheu pelo tipo real).
 * Os nomes são UUID e nunca mudam de conteúdo, então o navegador pode guardar em cache por muito tempo.
 */
@Configuration(proxyBeanMethods = false)
class ImagensPublicasConfig implements WebMvcConfigurer {

    private final String diretorio;

    ImagensPublicasConfig(@Value("${catalogo.imagens.diretorio:./dados/imagens}") String diretorio) {
        this.diretorio = diretorio;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/imagens/**")
                .addResourceLocations(Path.of(diretorio).toAbsolutePath().normalize().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
