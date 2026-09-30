package com.example.typecast.client;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TypecastProperties.class)
public class TypecastConfig {

    @Bean
    RestClient typecastRestClient(RestClient.Builder builder, TypecastProperties props) {
        // 워커가 응답 없는 호출에 무한정 묶이지 않도록 타임아웃 지정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return builder
                .requestFactory(requestFactory)
                .baseUrl(props.baseUrl())
                .defaultHeader("X-API-KEY", props.apiKey())
                .build();
    }
}
