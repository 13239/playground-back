package com.project.playground.domain.user.repository;

import com.project.playground.domain.user.entity.UserNicknameHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 회원별 인덱스로 집계하고 최신 ID부터 필요한 번호 페이지의 이력만 읽는다. */
public interface UserNicknameHistoryRepository extends JpaRepository<UserNicknameHistory, Long> {
    long countByUserId(Long userId);

    List<UserNicknameHistory> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
