package com.example.typecast.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.typecast.client.TtsRequest;
import com.example.typecast.client.TypecastClient;
import com.example.typecast.client.TypecastProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * concurrency 개수만큼 워커 스레드가 큐(tts_job 테이블)를 폴링하며 READY 작업을 id 순서대로 처리한다.
 */
@Component
public class TtsWorkers {

    private static final Logger log = LoggerFactory.getLogger(TtsWorkers.class);

    private final JobStore store;
    private final TtsBatchRepository batches;
    private final TtsJobRepository jobs;
    private final TypecastClient client;
    private final TypecastProperties props;
    private final QueueProperties queue;

    /** 같은 JVM 안의 워커끼리 선점 트랜잭션이 겹치지 않게 한다 (커밋까지 포함). */
    private final Object claimLock = new Object();
    /** 대기 중인 워커를 깨우는 신호 (새 배치 제출, 종료) */
    private final Object signal = new Object();
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean stopping;

    public TtsWorkers(JobStore store, TtsBatchRepository batches, TtsJobRepository jobs,
                      TypecastClient client, TypecastProperties props, QueueProperties queue) {
        this.store = store;
        this.batches = batches;
        this.jobs = jobs;
        this.client = client;
        this.props = props;
        this.queue = queue;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        int recovered = store.recoverAllRunning();
        if (recovered > 0) {
            log.info("이전 실행에서 중단된 작업 {}건을 READY로 복구", recovered);
        }
        for (int i = 1; i <= queue.concurrency(); i++) {
            String workerId = "tts-worker-" + i;
            threads.add(Thread.ofPlatform().name(workerId).start(() -> loop(workerId)));
        }
        log.info("TTS 워커 {}개 시작", queue.concurrency());
    }

    /** 새 작업이 들어왔을 때 폴링 간격을 기다리지 않고 바로 처리하도록 깨운다. */
    public void wakeUp() {
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    /** 새 작업 선점을 멈추고, 처리 중인 작업이 끝나길 최대 30초 기다린다. */
    @EventListener(ContextClosedEvent.class)
    public void stop() throws InterruptedException {
        stopping = true;
        wakeUp();
        for (Thread t : threads) {
            t.join(30_000);
        }
    }

    private void loop(String workerId) {
        while (!stopping) {
            try {
                Optional<TtsJob> claimed;
                synchronized (claimLock) {
                    claimed = store.claimNext(workerId);
                }
                if (claimed.isPresent()) {
                    process(claimed.get(), workerId);
                } else {
                    synchronized (signal) {
                        if (!stopping) {
                            signal.wait(queue.pollInterval().toMillis());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // DB 오류 등으로 루프가 죽지 않게 한다
                log.error("{} 루프 오류", workerId, e);
            }
        }
    }

    private void process(TtsJob job, String workerId) {
        TtsBatch batch = batches.findById(job.getBatchId()).orElseThrow();
        try {
            byte[] audio = client.textToSpeech(toRequest(batch, job));
            String relPath = save(batch, job, audio);
            store.succeed(job.getId(), workerId, relPath);
            log.info("batch {} #{} 완료 ({} bytes)", batch.getId(), job.getSeq(), audio.length);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            String msg = code + " " + abbreviate(e.getResponseBodyAsString());
            // 429(동시요청 초과), 5xx 는 일시적 → 재시도. 나머지 4xx(키/크레딧/파라미터)는 재시도해도 같으므로 실패.
            if (code == 429 || code >= 500) {
                store.retryOrFail(job.getId(), workerId, msg);
            } else {
                store.fail(job.getId(), workerId, msg);
            }
        } catch (ResourceAccessException e) {
            store.retryOrFail(job.getId(), workerId, "네트워크/타임아웃: " + abbreviate(e.getMessage()));
        } catch (Exception e) {
            log.error("batch {} #{} 처리 실패", batch.getId(), job.getSeq(), e);
            store.fail(job.getId(), workerId, abbreviate(e.toString()));
        }
    }

    private TtsRequest toRequest(TtsBatch batch, TtsJob job) {
        TtsRequest.Prompt prompt;
        if (StringUtils.hasText(batch.getEmotion())) {
            prompt = TtsRequest.Prompt.preset(batch.getEmotion(), batch.getIntensity());
        } else {
            // smart 모드: 앞뒤 항목을 문맥으로 넘겨 이어지는 낭독처럼 자연스럽게
            prompt = TtsRequest.Prompt.smart(neighborText(batch, job.getSeq() - 1),
                    neighborText(batch, job.getSeq() + 1));
        }
        return new TtsRequest(
                job.getText(),
                StringUtils.hasText(batch.getVoiceId()) ? batch.getVoiceId() : props.defaultVoiceId(),
                props.model(),
                StringUtils.hasText(batch.getLanguage()) ? batch.getLanguage() : null,
                prompt,
                new TtsRequest.Output(null, null, null, batch.getFormat()),
                null
        );
    }

    private String neighborText(TtsBatch batch, int seq) {
        if (seq < 1 || seq > batch.getTotal()) {
            return null;
        }
        return jobs.findByBatchIdAndSeq(batch.getId(), seq).map(TtsJob::getText).orElse(null);
    }

    /** {output-dir}/batch-{id}/{seq 4자리}.{format} 에 저장하고 output-dir 기준 상대경로를 반환. */
    private String save(TtsBatch batch, TtsJob job, byte[] audio) throws IOException {
        String relPath = "batch-" + batch.getId() + "/" + "%04d.%s".formatted(job.getSeq(), batch.getFormat());
        Path file = BatchService.outputRoot(props).resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.write(file, audio);
        return relPath;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
