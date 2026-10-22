package com.project.playground.domain.activitylog.listener;

import com.project.playground.domain.activitylog.event.ActivityLogEvent;
import com.project.playground.domain.activitylog.service.ActivityLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 요청 트랜잭션 결과에 맞춰 저장 시점을 정하고 실제 저장은 전용 스레드에 맡긴다.
 * 트랜잭션이 없는 요청(로그인)은 fallbackExecution으로 발행 즉시 처리한다.
 */
@Component
@RequiredArgsConstructor
public class ActivityLogListener {

    private final ActivityLogWriter activityLogWriter;

    // 성공 로그는 커밋된 결과만 저장한다. 롤백된 가입이 성공으로 남지 않게 한다.
    @TransactionalEventListener(condition = "#event.success", fallbackExecution = true)
    public void onSuccess(ActivityLogEvent event) {
        activityLogWriter.write(event);
    }

    // 실패 로그는 요청 트랜잭션이 롤백되어도 트랜잭션이 끝난 뒤 저장한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, condition = "!#event.success", fallbackExecution = true)
    public void onFailure(ActivityLogEvent event) {
        activityLogWriter.write(event);
    }
}
