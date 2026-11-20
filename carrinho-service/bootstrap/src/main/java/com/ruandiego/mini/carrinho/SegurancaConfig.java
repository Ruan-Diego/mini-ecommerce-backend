package com.ruandiego.mini.carrinho;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança do carrinho como resource server, diferente do pedido: aqui o visitante também usa a API
 * (identificado pelo X-Sessao-Id), então nenhuma rota exige token. Mas, se a requisição trouxer um
 * `Authorization: Bearer`, ele é validado de verdade (assinatura, emissor, prazo): token inválido ou expirado
 * dá 401, e token bom vira o Principal, cujo nome é o `sub` e identifica o cliente. Quem não manda token é
 * apenas anônimo. Ou seja, "ser cliente" passou a ter prova; antes era só um header.
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // API sem cookie de sessão
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras.anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }
}
