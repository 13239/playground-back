package com.project.playground.global.security.admin;

import com.project.playground.domain.authorization.enums.PermissionCode;

import java.lang.annotation.*;

/** 관리자 핸들러는 필요한 기능을 명시하며 선언하지 않은 관리 경로는 기본 거절한다. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdminPermission {
    PermissionCode[] value();
}

