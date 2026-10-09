package com.teamc.isow.backend.config;

import com.teamc.isow.backend.image.LocalImageWebConfig;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 認証・認可の設定。
 * 疎通確認・新規会員登録・ログイン・保存した画像の表示のみ認証なしで許可し、それ以外は JWT（Authorization: Bearer）による認証を必須とする。
 * 管理者向けの API（/api/admin/**）は、さらに管理者であることを必須とし、管理者でなければ 404 を返す。
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    private final AdminAuthorizationManager adminAuthorizationManager;

    public SecurityConfig(AdminAuthorizationManager adminAuthorizationManager) {
        this.adminAuthorizationManager = adminAuthorizationManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // JWT で毎回本人確認するため、サーバー側でセッションを持たない
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                // 保存した画像（ローカル保存時）。<img> タグからの読み込みには認証ヘッダーを付けられないため許可する
                // （ファイル名は推測できない乱数。クラウドストレージの公開 URL と同じ扱い）
                .requestMatchers(HttpMethod.GET, LocalImageWebConfig.URL_PATH + "**").permitAll()
                // サーバー内部エラーの応答が認証エラー(401)に置き換わらないよう、エラー画面は許可する
                .requestMatchers("/error").permitAll()
                // 管理者向けの API（docs/admin.md）。リクエストのたびに DB の role で管理者か確かめる。
                // 前方一致のため、管理 API を足すときに個別の設定は要らない。anyRequest より前に置くこと
                .requestMatchers("/api/admin/**").access(adminAuthorizationManager)
                .anyRequest().authenticated()
            )
            // Authorization: Bearer のトークンを JwtConfig の JwtDecoder で検証する（署名・有効期限）
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .exceptionHandling(ex -> ex
                // 未認証のリクエストは 403 ではなく 401 を返す（ログイン画面への誘導に使う）
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // 認証済みだが権限がない場合は、403 ではなく、存在しない URL と同じ 404 を返す
                // （管理者向けの API があることを、管理者でない人に分からせないため。docs/admin.md）。
                // sendError で返すため、存在しない URL と同じくエラー画面（/error）を通り、本文の形も同じになる。
                // 注意：現状この経路を通るのは /api/admin/** の規則だけである。
                // ほかに認可の規則（hasRole など）を足すと、それも 404 になるので注意すること
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.sendError(HttpStatus.NOT_FOUND.value()))
            );
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
