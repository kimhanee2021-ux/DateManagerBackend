package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserStyleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStyleServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserStyleRepository userStyleRepository;

  private UserStyleService userStyleService;
  private User user;

  @BeforeEach
  void setUp() {
    userStyleService = new UserStyleServiceImpl(userRepository, userStyleRepository);
    user = User.builder()
        .id(1L)
        .email("test@example.com")
        .nickname("테스트")
        .gender("UNKNOWN")
        .build();
  }

  @Test
  void savesAllOnboardingAxesToExistingUserStyleColumns() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(userStyleRepository.findById(1L)).thenReturn(Optional.empty());

    userStyleService.saveOnboarding(
        1L,
        new SaveOnboardingStyleRequest(70, 30, 50, 70, 30, 50)
    );

    ArgumentCaptor<UserStyle> captor = ArgumentCaptor.forClass(UserStyle.class);
    verify(userStyleRepository).save(captor.capture());
    UserStyle saved = captor.getValue();

    // 2026-08-22 - UserStyle 내부 저장값이 실시간 갱신 엔진 때문에 Double로 바뀌어서 기대값도
    // double로 맞춤(값 자체는 그대로 70/30/50 등, 타입만 변경).
    assertEquals(70.0, saved.getInitEnergy());
    assertEquals(30.0, saved.getInitImmersion());
    assertEquals(50.0, saved.getInitVibe());
    assertEquals(70.0, saved.getInitAesthetic());
    assertEquals(30.0, saved.getInitPacing());
    assertEquals(50.0, saved.getInitDepth());
  }

  @Test
  void repeatedRequestUpdatesTheSameUserStyleInsteadOfCreatingAnotherOne() {
    AtomicReference<UserStyle> stored = new AtomicReference<>();
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(userStyleRepository.findById(1L))
        .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
    when(userStyleRepository.save(any(UserStyle.class))).thenAnswer(invocation -> {
      UserStyle saved = invocation.getArgument(0);
      stored.set(saved);
      return saved;
    });

    SaveOnboardingStyleRequest request =
        new SaveOnboardingStyleRequest(70, 30, 50, 70, 30, 50);

    userStyleService.saveOnboarding(1L, request);
    UserStyle firstSaved = stored.get();
    userStyleService.saveOnboarding(1L, request);

    assertSame(firstSaved, stored.get());
  }

  @Test
  void rejectsScoresBelowZero() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> userStyleService.saveOnboarding(
            1L,
            new SaveOnboardingStyleRequest(-1, 30, 50, 70, 30, 50)
        )
    );

    assertEquals(400, exception.getStatusCode().value());
    verify(userStyleRepository, never()).save(any());
  }

  @Test
  void rejectsScoresAboveOneHundred() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> userStyleService.saveOnboarding(
            1L,
            new SaveOnboardingStyleRequest(70, 101, 50, 70, 30, 50)
        )
    );

    assertEquals(400, exception.getStatusCode().value());
    verify(userStyleRepository, never()).save(any());
  }
}
