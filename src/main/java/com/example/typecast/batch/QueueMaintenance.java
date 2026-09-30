package com.example.typecast.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueMaintenance {

    private final JobStore store;

    public QueueMaintenance(JobStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void run() {
        store.maintain();
    }
}
