package com.example.typecast.batch;

import java.util.EnumSet;
import java.util.Set;

/**
 * READY → RUNNING → SUCCEEDED | FAILED
 *                 ↘ RETRY_WAIT → (백오프 후) READY
 * READY | RETRY_WAIT → CANCELED
 */
public enum TtsJobStatus {
    READY,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELED;

    /** 아직 끝나지 않은 상태. 하나라도 남아 있으면 배치 진행 중. */
    public static final Set<TtsJobStatus> ACTIVE = EnumSet.of(READY, RUNNING, RETRY_WAIT);

    /** 취소 가능 상태. 이미 외부 API 호출 중인 RUNNING은 제외. */
    public static final Set<TtsJobStatus> CANCELABLE = EnumSet.of(READY, RETRY_WAIT);
}
