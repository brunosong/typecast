package com.example.typecast.batch;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TtsBatchRepository extends JpaRepository<TtsBatch, Long> {

    List<TtsBatch> findTop50ByOrderByIdDesc();
}
