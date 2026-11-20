package com.ruandiego.mini.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Prova a borda de segurança com JWTs de verdade: o teste gera um par RSA, publica a chave pública num
 * "Keycloak falso" (só o endpoint de JWKS) e assina os tokens com a chave privada. O gateway valida
 * assinatura, emissor e validade exatamente como faria com o Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySegurancaTest {

    private static final String EMISSOR = "http://keycloak.teste/realms/mini";
    private static final RSAKey CHAVE = gerarChave("chave-do-teste");
    private static final RSAKey OUTRA_CHAVE = gerarChave("chave-do-teste"); // mesmo kid, outro par: assinatura falsa
    private static final HttpServer JWKS = servidor(
            "{\"keys\":[" + CHAVE.toPublicJWK().toJSONString() + "]}");
    private static final HttpServer SERVICO = servidor("{\"ok\":true}");

    @DynamicPropertySource
    static void apontar(DynamicPropertyRegistry registro) {
        registro.add("KEYCLOAK_ISSUER", () -> EMISSOR);
        registro.add("KEYCLOAK_JWKS", () -> "http://localhost:" + JWKS.getAddress().getPort() + "/certs");
        registro.add("CATALOGO_URL", () -> "http://localhost:" + SERVICO.getAddress().getPort());
        registro.add("CARRINHO_URL", () -> "http://localhost:" + SERVICO.getAddress().getPort());
        registro.add("PEDIDO_URL", () -> "http://localhost:" + SERVICO.getAddress().getPort());
        registro.add("PAGAMENTO_URL", () -> "http://localhost:" + SERVICO.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        JWKS.stop(0);
        SERVICO.stop(0);
    }

    @Autowired
    private WebTestClient cliente;

    @Test
    void adminSemTokenRecebe401() {
        cliente.post().uri("/api/catalogo/admin/produtos").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void clienteEmRotaDeAdminRecebe403() {
        cliente.post().uri("/api/catalogo/admin/produtos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), 300))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void adminEmRotaDeAdminPassa() {
        cliente.post().uri("/api/catalogo/admin/produtos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente", "admin"), 300))
                .exchange().expectStatus().isOk();
    }

    @Test
    void rotaDeAdminDeOutroServicoTambemExigeOPapel() {
        cliente.get().uri("/api/estoque/admin/saldos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), 300))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void rotaProtegidaQueNaoEAdminExigeSoOToken() {
        cliente.get().uri("/api/pedidos/pedidos/meus").exchange().expectStatus().isUnauthorized();
        // Cliente autenticado (sem o papel admin) passa e a rota do pedido-service responde.
        cliente.get().uri("/api/pedidos/pedidos/meus")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), 300))
                .exchange().expectStatus().isOk();
        cliente.get().uri("/api/pedidos/fretes/cotacao?cep=01310100&subtotal=100")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void pagamentosExigemTokenMasOWebhookDoGatewayNao() {
        cliente.post().uri("/api/pagamentos/pagamentos").exchange().expectStatus().isUnauthorized();
        cliente.post().uri("/api/pagamentos/pagamentos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), 300))
                .exchange().expectStatus().isOk();
        // O webhook passa sem JWT (o segredo é conferido pelo pagamento-service, que aqui é um falso).
        cliente.post().uri("/api/pagamentos/webhooks/gateway").exchange().expectStatus().isOk();
        // Só o POST do webhook é livre.
        cliente.get().uri("/api/pagamentos/webhooks/gateway").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void estornoEhSoDoAdmin() {
        String uri = "/api/pagamentos/pagamentos/abc/estorno";
        cliente.post().uri(uri).exchange().expectStatus().isUnauthorized();
        cliente.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), 300))
                .exchange().expectStatus().isForbidden();
        cliente.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente", "admin"), 300))
                .exchange().expectStatus().isOk();
    }

    @Test
    void visitanteNavegaNoCatalogoEMontaCarrinhoSemToken() {
        cliente.get().uri("/api/catalogo/produtos").exchange().expectStatus().isOk();
        cliente.put().uri("/api/carrinho/carrinhos/atual/itens/abc").exchange().expectStatus().isOk();
    }

    @Test
    void escritaNoCatalogoForaDoAdminExigeToken() {
        cliente.post().uri("/api/catalogo/produtos").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenExpiradoRecebe401MesmoEmRotaLivre() {
        cliente.get().uri("/api/catalogo/produtos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CHAVE, List.of("cliente"), -300))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenAssinadoPorOutraChaveRecebe401() {
        cliente.post().uri("/api/catalogo/admin/produtos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OUTRA_CHAVE, List.of("admin"), 300))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenDeOutroEmissorRecebe401() {
        cliente.post().uri("/api/catalogo/admin/produtos")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(CHAVE, "http://outro.teste/realms/mini", List.of("admin"), 300))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void healthContinuaLivre() {
        cliente.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private static String token(RSAKey chave, List<String> papeis, long segundosDeValidade) {
        return token(chave, EMISSOR, papeis, segundosDeValidade);
    }

    private static String token(RSAKey chave, String emissor, List<String> papeis, long segundosDeValidade) {
        try {
            Instant agora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(emissor)
                    .subject("usuario-1")
                    .issueTime(Date.from(agora.minusSeconds(600)))
                    .expirationTime(Date.from(agora.plusSeconds(segundosDeValidade)))
                    .claim("realm_access", Map.of("roles", papeis))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(chave));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey gerarChave(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer servidor(String corpoJson) {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
                byte[] corpo = corpoJson.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(200, corpo.length);
                troca.getResponseBody().write(corpo);
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
