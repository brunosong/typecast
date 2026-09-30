package com.example.typecast.batch;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 한 번에 제출된 대량 변환 요청. 음성 옵션은 배치 단위로 공통 적용된다.
 */
@Entity
@Table(name = "tts_batch")
public class TtsBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String voiceId;
    private String language;
    /** 비어 있으면 smart 모드 (앞뒤 항목을 문맥으로 사용) */
    private String emotion;
    private Double intensity;
    private String format;
    private int total;
    private LocalDateTime createdAt;

    protected TtsBatch() {
    }

    public TtsBatch(String title, String voiceId, String language, String emotion, Double intensity,
                    String format, int total) {
        this.title = title;
        this.voiceId = voiceId;
        this.language = language;
        this.emotion = emotion;
        this.intensity = intensity;
        this.format = format;
        this.total = total;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getVoiceId() { return voiceId; }
    public String getLanguage() { return language; }
    public String getEmotion() { return emotion; }
    public Double getIntensity() { return intensity; }
    public String getFormat() { return format; }
    public int getTotal() { return total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
