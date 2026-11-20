package com.ruandiego.mini.pagamento;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Segurança do pagamento como resource server. O gateway já barra requisições sem token, mas o serviço não
 * confia só na borda: quem chegar direto na porta 8084 também precisa de um JWT válido.
 *
 * - `/webhooks/**`: sem JWT, porque quem chama é o gateway de pagamento. A proteção é o segredo
 *   compartilhado, conferido no WebhookController (sem o segredo certo, 401).
 * - estorno: só o papel admin;
 * - o resto dos endpoints de pagamento: cliente logado (o dono do pagamento é o `sub` do token).
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // API sem cookie de sessão
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/actuator/health/**", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pagamentos/*/estorno").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor())))
                .build();
    }

    /** Papéis do realm vêm em realm_access.roles; o Spring espera authorities ROLE_*. */
    private static JwtAuthenticationConverter conversor() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> acesso)
                    || !(acesso.get("roles") instanceof List<?> papeis)) {
                return List.<GrantedAuthority>of();
            }
            Collection<GrantedAuthority> autoridades = papeis.stream()
                    .map(papel -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + papel))
                    .toList();
            return autoridades;
        });
        return conversor;
    }
}
