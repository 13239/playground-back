package com.project.playground.domain.accesspolicy.redis;

import com.project.playground.domain.accesspolicy.AccessRestrictedException;
import com.project.playground.domain.accesspolicy.entity.AccessPolicy;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;

/** DB는 영구 원본, Redis 스냅샷은 모든 인증 경로가 원자적으로 읽는 집행 상태다. */
@Repository @RequiredArgsConstructor
public class RedisAccessPolicyRepository {
    public static final String KEY = "auth:access-policy:v1";
    private static final RedisScript<Long> PUBLISH = RedisScript.of(new ClassPathResource("redis/access/publish.lua"), Long.class);
    private final StringRedisTemplate redis;

    public static String revision(long version) { return String.format(Locale.ROOT, "%019d", version); }

    /** SQL 커밋 전 요청을 보류한다. 실패·롤백 후에도 재동기화 전까지 이전 정책으로 허용하지 않는다. */
    public void freeze() { redis.opsForHash().put(KEY, "state", "pending"); }

    public void synchronize(AccessPolicy policy) {
        var snapshot = new LinkedHashMap<String, String>();
        snapshot.put("state", "ready"); snapshot.put("version", revision(policy.getVersion()));
        snapshot.put("enabled", Boolean.toString(policy.isEnabled())); snapshot.put("message", policy.getMessage());
        policy.getExemptRoleCodes().stream().sorted().forEach(code -> snapshot.put("allow:" + code, "true"));
        policy.getRevocations().forEach((code, version) -> snapshot.put("revoke:" + code, revision(version)));
        // 버전이 같아도 일부 필드가 유실되었으면 원본 전체를 복구한다.
        if (snapshot.equals(redis.<String, String>opsForHash().entries(KEY))) return;
        var arguments = new ArrayList<String>();
        arguments.add(revision(policy.getVersion()));
        arguments.add(Boolean.toString(policy.isEnabled()));
        arguments.add(policy.getMessage());
        snapshot.forEach((field, value) -> {
            if (field.startsWith("allow:") || field.startsWith("revoke:")) { arguments.add(field); arguments.add(value); }
        });
        if (!Long.valueOf(1).equals(redis.execute(PUBLISH, List.of(KEY), arguments.toArray()))) {
            throw new BusinessException(ErrorCode.ACCESS_POLICY_UNAVAILABLE);
        }
    }

    /** 스크립트의 짧은 오류 표식을 사용자 안내로 변환한다. 일반 Redis 오류 원문은 공개하지 않는다. */
    public void requireResult(String result) {
        if ("ACCESS_RESTRICTED".equals(result)) {
            String message = redis.<String, String>opsForHash().get(KEY, "message");
            boolean safe = message != null && !message.isBlank() && message.length() <= 300
                    && message.codePoints().noneMatch(value -> Character.isISOControl(value) || Character.getType(value) == Character.FORMAT);
            throw new AccessRestrictedException(safe ? message : ErrorCode.ACCESS_RESTRICTED.getMessage());
        }
        if ("ACCESS_POLICY_UNAVAILABLE".equals(result)) throw new BusinessException(ErrorCode.ACCESS_POLICY_UNAVAILABLE);
    }
}
