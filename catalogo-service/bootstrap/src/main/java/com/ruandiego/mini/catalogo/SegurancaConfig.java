package com.ruandiego.mini.catalogo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
 * Segurança do catálogo como resource server. O gateway já barra tudo, mas o serviço não confia só na
 * borda: quem chegar direto na porta 8081 também precisa de um JWT válido com o papel admin para mexer
 * em /admin/**. A vitrine segue pública. É wiring de framework, por isso mora no bootstrap, e nem o
 * domínio nem a application sabem que existe autenticação.
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // API sem cookie de sessão
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/admin/**").hasRole("admin")
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
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
