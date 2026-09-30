package com.example.typecast.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 샘플 API 요청. emotion 이 비어 있으면 smart 모드, 값이 있으면 preset 모드.
 */
public record SpeakRequest(
        @NotBlank @Size(max = 2000) String text,
        String voiceId,
        String language,
        String emotion,
        Double intensity,
        String format,
        Double tempo,
        Integer pitch
) {
}
