package org.ict.datemanagerbackend.auth;

import org.ict.datemanagerbackend.config.JwtService;
import org.ict.datemanagerbackend.domain.user.entity.LoginLog;
import org.ict.datemanagerbackend.domain.user.entity.SocialAccount;
import org.ict.datemanagerbackend.domain.user.repository.LoginLogRepository;
import org.ict.datemanagerbackend.domain.user.repository.SocialAccountRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

// 구글/카카오 로그인이 "성공"했을 때(=OAuth2 제공자가 사용자 인증을 마쳤을 때) 스프링 시큐리티가
// 호출해주는 핸들러. 여기서 하는 일:
//   1) 구글/카카오가 준 사용자 정보(attrs)에서 provider별로 다른 필드명을 우리 쪽 공통 필드로 변환
//   2) 이미 연동된 계정인지 / 같은 이메일의 기존 계정이 있는지 확인해서 로그인시킬 User를 결정(또는 신규 생성)
//   3) 우리 서비스 자체 JWT를 발급해서 프론트엔드로 리다이렉트 (구글/카카오 토큰을 그대로 쓰지 않음)
// SecurityConfig에서 oauth2Login(oauth2 -> oauth2.successHandler(successHandler))로 등록되어 있다.
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final JwtService jwtService;
    private final LoginLogRepository loginLogRepository;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.mobile-redirect-base}")
    private String mobileRedirectBase;

    // 로그인 시작 시점에 PlatformAwareAuthorizationRequestRepository가 세션에 저장해둔 platform
    // 값을 보고, 안드로이드 앱발 요청이면 웹 주소(frontendBaseUrl, 실기기에선 도달 불가능한
    // localhost) 대신 앱이 AndroidManifest.xml에 등록해둔 커스텀 스킴 딥링크로 돌려보낸다.
    private String resolveRedirectBase(HttpServletRequest request) {
        Object platform = request.getSession(true)
                .getAttribute(PlatformAwareAuthorizationRequestRepository.OAUTH_PLATFORM_SESSION_KEY);
        if ("android".equals(platform)) {
            return mobileRedirectBase + "/callback";
        }
        return frontendBaseUrl + "/oauth/callback";
    }

    public OAuth2LoginSuccessHandler(UserRepository userRepository,
                                      SocialAccountRepository socialAccountRepository,
                                      OAuth2AuthorizedClientService authorizedClientService,
                                      JwtService jwtService,
                                      LoginLogRepository loginLogRepository) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.authorizedClientService = authorizedClientService;
        this.jwtService = jwtService;
        this.loginLogRepository = loginLogRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        // registrationId는 application.yaml에 등록한 이름 그대로 "google" 또는 "kakao"로 들어온다
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = authToken.getAuthorizedClientRegistrationId();
        // 구글/카카오가 각자 다른 JSON 구조로 사용자 정보를 내려주는데, attrs는 그걸 그대로 담은 맵
        Map<String, Object> attrs = ((OAuth2User) authentication.getPrincipal()).getAttributes();

        String providerUserId;
        String email;
        String nickname;

        if ("google".equals(registrationId)) {
            // 구글은 OIDC 표준 필드(sub=고유id, email, name)를 그대로 준다
            providerUserId = String.valueOf(attrs.get("sub"));
            email = (String) attrs.get("email");
            nickname = attrs.get("name") != null ? (String) attrs.get("name") : email;
        } else if ("kakao".equals(registrationId)) {
            // 카카오는 attrs.get("id")가 고유 id이고, 닉네임은 kakao_account.profile.nickname 안에 중첩되어 있음.
            // scope에 email 동의를 안 받았기 때문에(application.yaml scope: profile_nickname만 요청)
            // 실제 카카오 이메일은 안 주고, provider id로 우리 쪽에서 가짜 이메일을 만들어 쓴다.
            providerUserId = String.valueOf(attrs.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> kakaoAccount = (Map<String, Object>) attrs.get("kakao_account");
            String kakaoNickname = null;
            if (kakaoAccount != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                if (profile != null) {
                    kakaoNickname = (String) profile.get("nickname");
                }
            }
            nickname = kakaoNickname != null ? kakaoNickname : "카카오유저";
            email = "kakao_" + providerUserId + "@kakao.local";
        } else {
            // 네이버는 실제 사용자 정보가 최상위가 아니라 response 필드 안에 중첩되어 내려온다
            // (application.yaml의 provider.naver.user-name-attribute: response 설정과 연결됨)
            @SuppressWarnings("unchecked")
            Map<String, Object> naverResponse = (Map<String, Object>) attrs.get("response");
            providerUserId = String.valueOf(naverResponse.get("id"));
            String naverEmail = (String) naverResponse.get("email");
            String naverNickname = (String) naverResponse.get("name");
            nickname = naverNickname != null ? naverNickname : "네이버유저";
            // scope에 email 동의를 안 받은 경우 대비 (구글/카카오와 동일한 방식으로 대체 이메일 생성)
            email = naverEmail != null ? naverEmail : "naver_" + providerUserId + "@naver.local";
        }

        // DB의 social_accounts.provider 컬럼과 맞추기 위해 대문자로 통일 (GOOGLE / KAKAO)
        String providerKey = registrationId.toUpperCase();

        // 이미 이 소셜 계정으로 연동된 적이 있으면 -> 그 유저로 바로 로그인 (이메일 재확인 불필요)
        java.util.Optional<SocialAccount> existingLink =
                socialAccountRepository.findByProviderAndProviderUserId(providerKey, providerUserId);

        User user;
        if (existingLink.isPresent()) {
            // 이전에 이 소셜 계정으로 로그인한 적이 있음 -> social_accounts에 연결된 user를 그대로 사용
            user = userRepository.findById(existingLink.get().getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("연동된 유저를 찾을 수 없습니다"));
            if (user.getWithdrawnAt() != null) {
                String redirectUrl = resolveRedirectBase(request) + "?error="
                        + URLEncoder.encode("탈퇴 처리된 계정입니다", StandardCharsets.UTF_8);
                response.sendRedirect(redirectUrl);
                return;
            }
        } else {
            java.util.Optional<User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent() && existingByEmail.get().getPasswordHash() != null) {
                // 이미 이메일/비밀번호로 가입된 계정 -> 무단 연동 방지, 소셜 로그인 차단
                String redirectUrl = resolveRedirectBase(request) + "?error="
                        + URLEncoder.encode("이미 일반 회원가입으로 등록된 이메일입니다. 기존 계정으로 로그인해주세요", StandardCharsets.UTF_8);
                response.sendRedirect(redirectUrl);
                return;
            }
            // 같은 이메일의 순수 소셜 계정(다른 provider)이 있으면 그 유저에 연동, 없으면 신규 생성
            user = existingByEmail.orElseGet(() -> userRepository.save(User.builder()
                    .email(email)
                    .nickname(nickname)
                    .gender("UNKNOWN")
                    .passwordHash(null)
                    .build()));

            OAuth2AuthorizedClient authorizedClient =
                    authorizedClientService.loadAuthorizedClient(registrationId, authToken.getName());
            String accessTokenValue = authorizedClient != null
                    ? authorizedClient.getAccessToken().getTokenValue()
                    : null;
            socialAccountRepository.save(SocialAccount.builder()
                    .user(user)
                    .provider(providerKey)
                    .providerUserId(providerUserId)
                    .accessToken(accessTokenValue)
                    .build());
        }

        // 여기까지 오면 user가 확정된 상태. 관리자 대시보드 방문자 추이 그래프용 로그인 기록을 남기고,
        // 우리 서비스 전용 JWT를 만들어서 프론트엔드의 /oauth/callback 라우트로 쿼리스트링에 실어 리다이렉트한다.
        // (프론트는 이 token 쿼리파라미터를 읽어 localStorage에 저장 -> 이후 요청부터 Authorization 헤더로 사용)
        loginLogRepository.save(LoginLog.builder().user(user).loggedInAt(LocalDateTime.now()).build());
        String jwt = jwtService.generateToken(user.getId(), user.getEmail());
        String redirectUrl = resolveRedirectBase(request) + "?token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
