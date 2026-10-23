package com.project.playground.domain.accesspolicy.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 공통 검사와 업무 스크립트를 결합해 검사 직후 정책이 바뀌는 경쟁 구간을 없앤다. */
public final class AccessPolicyScripts {
    private AccessPolicyScripts() { }
    public static <T> RedisScript<T> script(String location, String guard, Class<T> resultType) {
        try {
            String common = new ClassPathResource("redis/access/gate.lua").getContentAsString(StandardCharsets.UTF_8);
            String body = new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
            return RedisScript.of(common + "\n" + guard + "\n" + body, resultType);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
