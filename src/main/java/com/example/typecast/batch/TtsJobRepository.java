package com.example.typecast.batch;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TtsJobRepository extends JpaRepository<TtsJob, Long> {

    @Query("select j.id from TtsJob j where j.status = :status order by j.id")
    List<Long> findIdsByStatus(TtsJobStatus status, Limit limit);

    /**
     * 조건부 UPDATE로 선점. 다른 워커(또는 다른 인스턴스)가 먼저 가져갔으면 0 이 반환된다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update TtsJob j set j.status = com.example.typecast.batch.TtsJobStatus.RUNNING,
                   j.workerId = :workerId, j.claimedAt = :now
             where j.id = :id and j.status = com.example.typecast.batch.TtsJobStatus.READY""")
    int tryClaim(Long id, String workerId, LocalDateTime now);

    @Modifying
    @Query("""
            update TtsJob j set j.status = com.example.typecast.batch.TtsJobStatus.READY, j.nextRunAt = null
             where j.status = com.example.typecast.batch.TtsJobStatus.RETRY_WAIT and j.nextRunAt <= :now""")
    int releaseDueRetries(LocalDateTime now);

    @Modifying
    @Query("""
            update TtsJob j set j.status = com.example.typecast.batch.TtsJobStatus.READY, j.workerId = null
             where j.status = com.example.typecast.batch.TtsJobStatus.RUNNING and j.claimedAt < :before""")
    int releaseStale(LocalDateTime before);

    @Modifying
    @Query("""
            update TtsJob j set j.status = com.example.typecast.batch.TtsJobStatus.CANCELED, j.doneAt = :now
             where j.batchId = :batchId and j.status in :from""")
    int cancel(Long batchId, Collection<TtsJobStatus> from, LocalDateTime now);

    @Modifying
    @Query("""
            update TtsJob j set j.status = com.example.typecast.batch.TtsJobStatus.READY,
                   j.retryCnt = 0, j.resultMsg = null, j.doneAt = null
             where j.batchId = :batchId and j.status = com.example.typecast.batch.TtsJobStatus.FAILED""")
    int requeueFailed(Long batchId);

    @Query("select j.status, count(j) from TtsJob j where j.batchId = :batchId group by j.status")
    List<Object[]> countByStatus(Long batchId);

    List<TtsJob> findByBatchIdOrderBySeq(Long batchId);

    List<TtsJob> findByBatchIdAndStatusOrderBySeq(Long batchId, TtsJobStatus status);

    Optional<TtsJob> findByBatchIdAndSeq(Long batchId, int seq);
}
