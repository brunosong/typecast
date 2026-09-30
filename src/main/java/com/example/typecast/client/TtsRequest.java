package com.example.typecast.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /v1/text-to-speech 요청 바디.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TtsRequest(
        String text,
        @JsonProperty("voice_id") String voiceId,
        String model,
        String language,
        Prompt prompt,
        Output output,
        Integer seed
) {

    /**
     * emotion_type = "smart": 앞뒤 문맥으로 감정 추론
     * emotion_type = "preset": normal, happy, sad, angry, whisper, toneup, tonedown 중 지정
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prompt(
            @JsonProperty("emotion_type") String emotionType,
            @JsonProperty("emotion_preset") String emotionPreset,
            @JsonProperty("emotion_intensity") Double emotionIntensity,
            @JsonProperty("previous_text") String previousText,
            @JsonProperty("next_text") String nextText
    ) {
        public static Prompt smart(String previousText, String nextText) {
            return new Prompt("smart", null, null, previousText, nextText);
        }

        public static Prompt preset(String preset, Double intensity) {
            return new Prompt("preset", preset, intensity, null, null);
        }
    }

    /**
     * volume(기본 100), audio_pitch(반음 단위, 기본 0), audio_tempo(기본 1.0), audio_format(wav|mp3)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Output(
            Integer volume,
            @JsonProperty("audio_pitch") Integer audioPitch,
            @JsonProperty("audio_tempo") Double audioTempo,
            @JsonProperty("audio_format") String audioFormat
    ) {
    }
}
