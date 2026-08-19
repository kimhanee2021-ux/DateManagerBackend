package org.ict.datemanagerbackend.domain.aichat.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.aichat.dto.Response.AiChatMessageResponse;
import org.ict.datemanagerbackend.domain.aichat.dto.Response.AiChatSessionResponse;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessage;
import org.ict.datemanagerbackend.domain.aichat.entity.AiChatSession;
import org.ict.datemanagerbackend.domain.aichat.service.AiChatService;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 챗봇 최소 기능 컨트롤러 - 세션 생성, 메시지 보내기(+AI 응답), 메시지 이력 조회 3개만 제공한다.
 * 의도 분석(AiChatMessageIntent)/성향 점수 추출(AiChatMessageScore)은 이번 범위에서 제외했다
 * (별도 분석 로직이 필요해 최소 기능 범위를 넘어서서, 엔티티만 두고 손대지 않음).
 */
@RestController
@RequestMapping("/api/aichat")
@RequiredArgsConstructor
public class AiChatController {

  private final AiChatService aiChatService;
  private final UserRepository userRepository;

  // CoupleController와 동일한 패턴: JwtAuthFilter가 세팅해둔 principal(userId)로 User를 조회한다.
  private User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  private Double parseNullableDouble(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** POST /api/aichat/sessions - 새 채팅 세션 시작 */
  @PostMapping("/sessions")
  public ResponseEntity<?> createSession(Authentication authentication, @RequestBody(required = false) Map<String, String> body) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    String title = body != null ? body.get("title") : null;
    AiChatSession session = aiChatService.createSession(me, title);
    return ResponseEntity.ok(new AiChatSessionResponse(session.getId(), session.getTitle(), session.getCreatedAt()));
  }

  /** POST /api/aichat/sessions/{sessionId}/messages - 메시지 보내고 AI 응답 받기 */
  @PostMapping("/sessions/{sessionId}/messages")
  public ResponseEntity<?> sendMessage(Authentication authentication, @PathVariable Long sessionId,
                                        @RequestBody Map<String, String> body) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    String text = body.get("text");
    if (text == null || text.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "메시지 내용(text)이 비어있습니다"));
    }
    // lat/lon은 선택값 - 프론트가 위치 권한을 안 줬으면 안 보낼 수 있어서 없으면 null로 둔다.
    Double lat = parseNullableDouble(body.get("lat"));
    Double lon = parseNullableDouble(body.get("lon"));

    try {
      AiChatMessage aiMessage = aiChatService.sendMessage(me, sessionId, text, lat, lon);
      return ResponseEntity.ok(new AiChatMessageResponse(
          aiMessage.getId(), aiMessage.getSenderType(), aiMessage.getMessageText(), aiMessage.getCreatedAt()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      // OpenAI 결제 미등록(401)이나 한도 초과(429) 등 외부 API 호출 실패를 여기서 잡는다.
      return ResponseEntity.status(502).body(Map.of("error", "AI 응답을 받는 중 오류가 발생했습니다: " + e.getMessage()));
    }
  }

  /** GET /api/aichat/sessions/{sessionId}/messages - 메시지 이력 조회 */
  @GetMapping("/sessions/{sessionId}/messages")
  public ResponseEntity<?> getMessages(Authentication authentication, @PathVariable Long sessionId) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      List<AiChatMessageResponse> messages = aiChatService.getMessages(me, sessionId).stream()
          .map(m -> new AiChatMessageResponse(m.getId(), m.getSenderType(), m.getMessageText(), m.getCreatedAt()))
          .toList();
      return ResponseEntity.ok(messages);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

}
