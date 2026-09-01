package org.ict.datemanagerbackend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

// 기본 HttpSessionOAuth2AuthorizationRequestRepository를 감싸서, 안드로이드 앱이 소셜로그인을
// 시작할 때(/oauth2/authorization/google?platform=android) 붙여 보내는 platform 파라미터를
// 세션에 같이 저장해둔다. OAuth2LoginSuccessHandler가 로그인 완료 시점에 이 값을 세션에서 다시
// 읽어서, 웹이면 프론트 URL로, 안드로이드 앱이면 커스텀 스킴 딥링크로 리다이렉트 대상을 나눈다
// (2026-09-01 - "구글/카카오 소셜로그인 딥링크" 미해결 항목 해결).
@Component
public class PlatformAwareAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH_PLATFORM_SESSION_KEY = "oauth_platform";

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        String platform = request.getParameter("platform");
        if (platform != null && !platform.isBlank()) {
            request.getSession(true).setAttribute(OAUTH_PLATFORM_SESSION_KEY, platform);
        }
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        return delegate.removeAuthorizationRequest(request, response);
    }
}
