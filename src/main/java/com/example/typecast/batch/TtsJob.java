package com.example.typecast.batch;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 배치 안의 한 항목(= TTS 호출 1회). 처리 순서는 id 오름차순이며 seq는 배치 내 순번(파일명에 사용).
 */
@Entity
@Table(name = "tts_job", indexes = {
        @Index(name = "ix_job_claim", columnList = "status, id"),
        @Index(name = "ix_job_batch", columnList = "batchId, seq")
})
public class TtsJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long batchId;
    private int seq;

    @Column(length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TtsJobStatus status;

    private int retryCnt;
    private LocalDateTime nextRunAt;
    private String workerId;
    private LocalDateTime claimedAt;
    private String filePath;

    @Column(length = 1000)
    private String resultMsg;

    private LocalDateTime createdAt;
    private LocalDateTime doneAt;

    protected TtsJob() {
    }

    public TtsJob(Long batchId, int seq, String text) {
        this.batchId = batchId;
        this.seq = seq;
        this.text = text;
        this.status = TtsJobStatus.READY;
        this.createdAt = LocalDateTime.now();
    }

    /** 이 워커가 아직 이 작업을 점유하고 있는지 (복구 로직에 의해 회수됐으면 false) */
    public boolean isOwnedBy(String workerId) {
        return status == TtsJobStatus.RUNNING && workerId.equals(this.workerId);
    }

    public void succeed(String filePath) {
        this.status = TtsJobStatus.SUCCEEDED;
        this.filePath = filePath;
        this.resultMsg = null;
        this.doneAt = LocalDateTime.now();
    }

    public void retryLater(String msg, LocalDateTime nextRunAt) {
        this.status = TtsJobStatus.RETRY_WAIT;
        this.retryCnt++;
        this.resultMsg = msg;
        this.nextRunAt = nextRunAt;
    }

    public void fail(String msg) {
        this.status = TtsJobStatus.FAILED;
        this.resultMsg = msg;
        this.doneAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public int getSeq() { return seq; }
    public String getText() { return text; }
    public TtsJobStatus getStatus() { return status; }
    public int getRetryCnt() { return retryCnt; }
    public String getFilePath() { return filePath; }
    public String getResultMsg() { return resultMsg; }
    public LocalDateTime getDoneAt() { return doneAt; }
}
