package com.example.typecast.batch;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "typecast.queue")
public record QueueProperties(
        @DefaultValue("2") int concurrency,
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("3") int maxRetry,
        @DefaultValue("10s") Duration retryBackoff,
        @DefaultValue("3m") Duration staleAfter,
        @DefaultValue("1000") int maxItems
) {
}
