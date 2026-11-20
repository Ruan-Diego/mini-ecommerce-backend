package com.ruandiego.mini.catalogo.application;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;

import java.util.Arrays;

/**
 * Arquivo de imagem recebido no upload, já conferido: não vazio, até {@link #TAMANHO_MAXIMO} bytes e de um dos
 * tipos aceitos. O tipo é descoberto pelos primeiros bytes (a "assinatura" do formato) e não pelo Content-Type
 * ou pela extensão, que quem envia pode escrever o que quiser.
 */
public final class ArquivoImagem {

    public static final int TAMANHO_MAXIMO = 2 * 1024 * 1024;

    public enum Tipo {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mime;
        private final String extensao;

        Tipo(String mime, String extensao) {
            this.mime = mime;
            this.extensao = extensao;
        }

        public String mime() {
            return mime;
        }

        public String extensao() {
            return extensao;
        }
    }

    private final byte[] conteudo;
    private final Tipo tipo;

    private ArquivoImagem(byte[] conteudo, Tipo tipo) {
        this.conteudo = conteudo;
        this.tipo = tipo;
    }

    public static ArquivoImagem de(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("o arquivo da imagem está vazio");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("a imagem deve ter até 2 MB");
        }
        Tipo tipo = detectar(conteudo);
        if (tipo == null) {
            throw new RegraDeNegocioException("a imagem deve ser JPEG, PNG ou WebP");
        }
        return new ArquivoImagem(conteudo.clone(), tipo);
    }

    private static Tipo detectar(byte[] b) {
        if (comeca(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Tipo.JPEG;
        }
        if (comeca(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Tipo.PNG;
        }
        if (comeca(b, 0, 'R', 'I', 'F', 'F') && comeca(b, 8, 'W', 'E', 'B', 'P')) {
            return Tipo.WEBP;
        }
        return null;
    }

    private static boolean comeca(byte[] b, int inicio, int... esperado) {
        if (b.length < inicio + esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((b[inicio + i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    public byte[] conteudo() {
        return conteudo.clone();
    }

    public Tipo tipo() {
        return tipo;
    }

    public int tamanho() {
        return conteudo.length;
    }

    @Override
    public String toString() {
        return "ArquivoImagem[" + tipo + ", " + conteudo.length + " bytes]";
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof ArquivoImagem a && tipo == a.tipo && Arrays.equals(conteudo, a.conteudo);
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + Arrays.hashCode(conteudo);
    }
}
