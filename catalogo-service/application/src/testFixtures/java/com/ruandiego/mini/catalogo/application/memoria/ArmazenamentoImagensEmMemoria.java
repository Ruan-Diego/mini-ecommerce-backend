package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.application.port.out.ArmazenamentoImagens;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Adapter em memória do ArmazenamentoImagens: guarda os arquivos num mapa por URL. */
public class ArmazenamentoImagensEmMemoria implements ArmazenamentoImagens {

    public static final String BASE = "http://imagens.teste/";

    private final Map<String, ArquivoImagem> arquivos = new LinkedHashMap<>();

    @Override
    public String guardar(ArquivoImagem arquivo) {
        String url = BASE + UUID.randomUUID() + "." + arquivo.tipo().extensao();
        arquivos.put(url, arquivo);
        return url;
    }

    public Map<String, ArquivoImagem> guardados() {
        return Map.copyOf(arquivos);
    }

    public void limpar() {
        arquivos.clear();
    }
}
