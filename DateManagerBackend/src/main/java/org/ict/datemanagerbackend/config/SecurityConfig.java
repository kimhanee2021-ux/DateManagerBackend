package org.ict.datemanagerbackend.config;

import org.ict.datemanagerbackend.auth.JwtAuthFilter;
import org.ict.datemanagerbackend.auth.OAuth2LoginSuccessHandler;
import org.ict.datemanagerbackend.auth.PlatformAwareAuthorizationRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// 스프링 시큐리티 전체 설정 - 어떤 요청을 막고 열지, 로그인 방식은 무엇인지 여기서 결정한다.
// 이 앱은 세션 기반이 아니라 JWT 기반 인증이라, 로그인 성공 후에는 세션 쿠키 대신
// JwtAuthFilter가 매 요청마다 헤더의 JWT를 검사하는 방식으로 동작한다.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    // 비밀번호 해시에 사용할 인코더. 회원가입/로그인 시 평문 비밀번호를 절대 그대로 저장/비교하지 않는다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 요청이 들어왔을 때 거치는 보안 필터 체인을 정의한다.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            OAuth2LoginSuccessHandler successHandler,
                                            PlatformAwareAuthorizationRequestRepository authorizationRequestRepository,
                                            JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // JWT는 쿠키가 아니라 헤더로 전달되므로 CSRF 공격 대상이 아님 -> 비활성화
                .csrf(csrf -> csrf.disable())
                // 아래 corsConfigurationSource() 빈에 정의된 설정을 사용
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // 소셜로그인 진입/콜백 경로, 이메일 로그인/회원가입, 헬스체크는 인증 없이 허용
                        .requestMatchers("/oauth2/**", "/login/**", "/api/auth/**", "/api/health","/swagger-ui/**").permitAll()
                        // 장소 조회는 로그인 없이 보이는 홈 탭 추천 카드에서도 쓰이므로 GET만 공개
                        .requestMatchers(HttpMethod.GET, "/api/places/**").permitAll()
                        // 날씨도 홈 탭 배너에 로그인 여부와 상관없이 보여야 해서 공개(유저 개인정보 아님)
                        .requestMatchers(HttpMethod.GET, "/api/weather").permitAll()
                        // 그 외 /api/** 는 반드시 로그인(JWT)이 있어야 접근 가능
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // 구글/카카오 OAuth2 로그인 활성화. 로그인 성공 시 처리는 OAuth2LoginSuccessHandler에 위임
                // (여기에 failureHandler가 없으면 인증 실패 시 스프링 기본 동작인 "/login?error"로 리다이렉트되니 주의)
                .oauth2Login(oauth2 -> oauth2.successHandler(successHandler)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(authorizationRequestRepository)))
                // 이메일 로그인용 필터보다 먼저 JWT 검사 필터를 태워서, 헤더에 유효한 토큰이 있으면
                // 이 시점에 SecurityContext에 인증 정보를 채워둔다
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 프론트엔드 origin에서 오는 요청만 허용하는 CORS 설정.
    // app.frontend-base-url과 실제 프론트 실행 주소가 다르면 브라우저가 요청을 막아버린다.
    // "http://localhost"(포트 없음)는 안드로이드 앱(Capacitor, capacitor.config.json의
    // androidScheme: "http")이 웹뷰 안에서 쓰는 origin이다 - 이게 빠져있으면 안드로이드 앱에서
    // 모든 API 호출이 CORS로 막힌다(2026-08-24 실기기 테스트로 발견).
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendBaseUrl, "http://localhost"));
        // PATCH가 빠져있어서 코스 순서 변경(PATCH /api/course/groups/{id}/items/{id}/move)
        // 같은 PATCH 요청이 브라우저 프리플라이트(OPTIONS) 단계에서 전부 CORS로 막히고 있었다
        // ("순서 변경 중 오류가 발생했습니다" - curl로는 프리플라이트가 없어 재현이 안 됐고,
        // 실제 브라우저에서만 발생. 2026-08-25 발견).
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
