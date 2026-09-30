package com.example.typecast.web;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Typecast API 에러(401 키 오류, 402 크레딧 부족, 422 파라미터 오류 등)를 상태코드 그대로 전달. */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleTypecastError(RestClientResponseException e) {
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(e.getResponseBodyAsString());
    }

    /** 배치 입력 검증 실패 (항목 없음, 개수 초과, 2000자 초과 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
