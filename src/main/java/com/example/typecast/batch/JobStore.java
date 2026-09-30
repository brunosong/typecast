package com.example.typecast.batch;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업 상태 전이를 짧은 트랜잭션 단위로 처리한다.
 * 외부 API 호출은 트랜잭션 밖(워커)에서 하고, 선점/결과 반영만 여기서 한다.
 */
@Service
public class JobStore {

    private static final Logger log = LoggerFactory.getLogger(JobStore.class);

    private final TtsJobRepository jobs;
    private final QueueProperties queue;

    public JobStore(TtsJobRepository jobs, QueueProperties queue) {
        this.jobs = jobs;
        this.queue = queue;
    }

    /** 가장 오래된 READY 작업 하나를 선점한다. */
    @Transactional
    public Optional<TtsJob> claimNext(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : jobs.findIdsByStatus(TtsJobStatus.READY, Limit.of(5))) {
            if (jobs.tryClaim(id, workerId, now) == 1) {
                return jobs.findById(id);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void succeed(Long jobId, String workerId, String filePath) {
        owned(jobId, workerId).ifPresent(job -> job.succeed(filePath));
    }

    /** 일시적 오류: 재시도 횟수가 남았으면 지수 백오프 후 재시도, 아니면 실패 처리. */
    @Transactional
    public void retryOrFail(Long jobId, String workerId, String msg) {
        owned(jobId, workerId).ifPresent(job -> {
            if (job.getRetryCnt() >= queue.maxRetry()) {
                job.fail("재시도 " + queue.maxRetry() + "회 초과: " + msg);
                return;
            }
            long delaySec = queue.retryBackoff().toSeconds() << job.getRetryCnt();
            job.retryLater(msg, LocalDateTime.now().plusSeconds(delaySec));
            log.info("job {} 재시도 대기 {}s ({})", jobId, delaySec, msg);
        });
    }

    @Transactional
    public void fail(Long jobId, String workerId, String msg) {
        owned(jobId, workerId).ifPresent(job -> job.fail(msg));
    }

    /** 시작 시 이전 실행에서 RUNNING으로 남은 작업을 모두 READY로 되돌린다 (단일 인스턴스 가정). */
    @Transactional
    public int recoverAllRunning() {
        return jobs.releaseStale(LocalDateTime.now().plusSeconds(1));
    }

    /** 백오프가 끝난 RETRY_WAIT와 오래 멈춘 RUNNING을 READY로 되돌린다. */
    @Transactional
    public void maintain() {
        LocalDateTime now = LocalDateTime.now();
        int retried = jobs.releaseDueRetries(now);
        int stale = jobs.releaseStale(now.minus(queue.staleAfter()));
        if (retried > 0 || stale > 0) {
            log.info("유지보수: 재시도 복귀 {}건, 멈춘 작업 복귀 {}건", retried, stale);
        }
    }

    /** 복구 로직에 의해 이미 회수된 작업이면 결과를 버린다. */
    private Optional<TtsJob> owned(Long jobId, String workerId) {
        Optional<TtsJob> job = jobs.findById(jobId).filter(j -> j.isOwnedBy(workerId));
        if (job.isEmpty()) {
            log.warn("job {} 은(는) 더 이상 {} 소유가 아니어서 결과를 반영하지 않음", jobId, workerId);
        }
        return job;
    }
}
