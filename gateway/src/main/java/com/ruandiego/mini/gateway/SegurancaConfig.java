package com.ruandiego.mini.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Borda de segurança: o gateway é um resource server OAuth2. Valida a assinatura, o emissor e a validade
 * do JWT do Keycloak antes de repassar a requisição para qualquer serviço.
 *
 * Regras (a ordem importa, vale a primeira que casar):
 * - pré-voo CORS e health: livres;
 * - qualquer /admin/** de qualquer serviço: papel admin (sem token → 401, token sem o papel → 403);
 * - catálogo (leitura) e carrinho: livres, porque visitante também navega e monta carrinho;
 * - todo o resto (pedidos, pagamentos, conta): exige token.
 * Token inválido ou expirado é 401 mesmo numa rota livre: quem manda token, manda um token bom.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SegurancaConfig {

    @Bean
    SecurityWebFilterChain cadeiaDeSeguranca(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // API sem cookie de sessão: não há o que forjar
                .authorizeExchange(regras -> regras
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/api/*/admin/**").hasRole("admin")
                        .pathMatchers(HttpMethod.GET, "/api/catalogo/**").permitAll()
                        .pathMatchers("/api/carrinho/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor())))
                .build();
    }

    /** O Keycloak põe os papéis do realm em realm_access.roles; o Spring espera authorities ROLE_*. */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> conversor() {
        return new ReactiveJwtAuthenticationConverterAdapter(jwt ->
                new JwtAuthenticationToken(jwt, papeisDoRealm(jwt)));
    }

    private static Collection<GrantedAuthority> papeisDoRealm(Jwt jwt) {
        if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> acesso)
                || !(acesso.get("roles") instanceof List<?> papeis)) {
            return List.of();
        }
        return papeis.stream()
                .map(papel -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + papel))
                .toList();
    }
}
