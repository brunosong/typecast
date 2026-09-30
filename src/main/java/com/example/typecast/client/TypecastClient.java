package com.example.typecast.client;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TypecastClient {

    private final RestClient restClient;

    public TypecastClient(RestClient typecastRestClient) {
        this.restClient = typecastRestClient;
    }

    /** 텍스트를 음성으로 변환하고 오디오 바이너리(wav/mp3)를 반환한다. */
    public byte[] textToSpeech(TtsRequest request) {
        return restClient.post()
                .uri("/v1/text-to-speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(byte[].class);
    }

    /** 보이스 목록 조회 (응답 JSON을 그대로 반환). */
    public String voices(String model, String gender) {
        return restClient.get()
                .uri(b -> b.path("/v3/voices")
                        .queryParamIfPresent("model", Optional.ofNullable(model))
                        .queryParamIfPresent("gender", Optional.ofNullable(gender))
                        .build())
                .retrieve()
                .body(String.class);
    }

    /** 플랜/잔여 크레딧 조회. */
    public String subscription() {
        return restClient.get()
                .uri("/v1/users/me/subscription")
                .retrieve()
                .body(String.class);
    }
}
