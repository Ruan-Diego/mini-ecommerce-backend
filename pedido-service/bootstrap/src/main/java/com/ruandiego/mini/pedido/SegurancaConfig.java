package com.ruandiego.mini.pedido;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Segurança do pedido como resource server. O gateway já barra requisições sem token, mas o serviço não
 * confia só na borda: quem chegar direto na porta 8083 também precisa de um JWT válido. Os endpoints de
 * pedido e de frete são de cliente logado (o dono do pedido é o `sub` do token); `/admin/**` exige o papel
 * admin do realm.
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // API sem cookie de sessão
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/admin/**").hasRole("admin")
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
