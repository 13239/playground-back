package com.project.playground.domain.accesspolicy.repository;

import com.project.playground.domain.accesspolicy.entity.AccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/** 정책 조회·변경은 서비스가 공통 역할 잠금을 확보한 뒤 수행한다. */
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> { }
