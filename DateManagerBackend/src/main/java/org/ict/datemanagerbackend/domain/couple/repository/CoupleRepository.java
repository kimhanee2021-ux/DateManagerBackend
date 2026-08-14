package org.ict.datemanagerbackend.domain.couple.repository;

import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// JpaRepository<Couple, Long>만 상속하면 save/findById/findAll/delete 같은 기본 CRUD 메서드를
// Spring Data JPA가 인터페이스 선언만 보고 알아서 구현해준다(직접 SQL이나 구현 클래스를 안 짜도 됨).
// <Couple, Long>의 의미: 이 리포지토리가 다루는 엔티티는 Couple, 그 PK 타입은 Long이라는 뜻.
public interface CoupleRepository extends JpaRepository<Couple, Long> {

    // 관리자 대시보드 "매칭된 커플" 통계용 - 해제된(DISCONNECTED) 커플까지 세면 실제보다
    // 부풀려지므로 활성 커플만 센다.
    long countByStatus(String status);

    // 두 사람이 과거에 연결됐다가 해제한 적(DISCONNECTED)이 있는지 찾는다. 두 사람의 user_id가
    // 각각 CoupleMember로 같은 Couple에 걸려있는지를 EXISTS 서브쿼리 두 개로 확인하는 방식이라,
    // 순서(userA/userB) 상관없이 "이 두 사람 조합"을 찾을 수 있다.
    // acceptInvite()가 재결합 시 이 메서드로 기존 row를 찾아 재활성화한다 - 매번 새 Couple을
    // 만들면 (1) 관리자 통계가 같은 커플을 중복으로 세고 (2) 예전 Couple에 달려있던 기념일/코스/
    // AI챗 기록이 새 Couple로 안 이어져서 사라진 것처럼 보이는 두 가지 문제가 있었다.
    // List로 받는 이유: 같은 두 사람이 여러 번 헤어졌다 만났다 해서 DISCONNECTED row가 여러 개
    // 쌓여있는 케이스가 있을 수 있다(Optional로 받으면 그런 데이터에서 NonUniqueResultException이
    // 터진다) - id 내림차순으로 정렬해서 호출부가 가장 최근 것 하나만 골라 쓰게 한다.
    @Query("SELECT c FROM Couple c WHERE c.status = 'DISCONNECTED' "
            + "AND EXISTS (SELECT 1 FROM CoupleMember m1 WHERE m1.couple = c AND m1.user.id = :userA) "
            + "AND EXISTS (SELECT 1 FROM CoupleMember m2 WHERE m2.couple = c AND m2.user.id = :userB) "
            + "ORDER BY c.id DESC")
    List<Couple> findDisconnectedCouplesBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
