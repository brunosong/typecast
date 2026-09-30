package com.example.typecast.batch;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.example.typecast.client.TypecastProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BatchService {

    private static final int MAX_TEXT_LENGTH = 2000;

    private final TtsBatchRepository batches;
    private final TtsJobRepository jobs;
    private final TypecastProperties props;
    private final QueueProperties queue;

    public BatchService(TtsBatchRepository batches, TtsJobRepository jobs,
                        TypecastProperties props, QueueProperties queue) {
        this.batches = batches;
        this.jobs = jobs;
        this.props = props;
        this.queue = queue;
    }

    public record Submit(String title, String voiceId, String language, String emotion, Double intensity,
                         String format, List<String> items) {
    }

    public record BatchView(Long id, String title, String voiceId, String emotion, String format, int total,
                            Map<TtsJobStatus, Long> counts, boolean done, LocalDateTime createdAt) {
    }

    public record JobView(Long id, int seq, String text, TtsJobStatus status, int retryCnt, String resultMsg,
                          LocalDateTime doneAt) {
    }

    public record BatchDetail(BatchView batch, List<JobView> jobs) {
    }

    /** 항목 목록을 검증하고 배치 1건 + 항목 N건을 READY로 저장한다. 하나라도 잘못되면 전체 거절. */
    @Transactional
    public BatchView submit(Submit req) {
        List<String> items = req.items() == null ? List.of() : req.items().stream()
                .map(s -> s == null ? "" : s.strip())
                .filter(s -> !s.isEmpty())
                .toList();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("변환할 텍스트가 없습니다.");
        }
        if (items.size() > queue.maxItems()) {
            throw new IllegalArgumentException("한 배치는 최대 " + queue.maxItems() + "개까지 가능합니다. (요청: " + items.size() + ")");
        }
        List<Integer> tooLong = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).length() > MAX_TEXT_LENGTH) {
                tooLong.add(i + 1);
            }
        }
        if (!tooLong.isEmpty()) {
            throw new IllegalArgumentException(MAX_TEXT_LENGTH + "자를 넘는 항목이 있습니다. 순번: " + tooLong);
        }

        String format = "mp3".equalsIgnoreCase(req.format()) ? "mp3" : "wav";
        String title = StringUtils.hasText(req.title()) ? req.title().strip()
                : items.getFirst().substring(0, Math.min(30, items.getFirst().length()));
        TtsBatch batch = batches.save(new TtsBatch(title, blankToNull(req.voiceId()), blankToNull(req.language()),
                blankToNull(req.emotion()), req.intensity(), format, items.size()));
        // DB가 재시작마다 새로 만들어져 배치 번호가 다시 1부터 시작하므로, 같은 번호의 이전 오디오 폴더를 비운다
        clearBatchDir(batch.getId());

        List<TtsJob> newJobs = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            newJobs.add(new TtsJob(batch.getId(), i + 1, items.get(i)));
        }
        jobs.saveAll(newJobs);
        return view(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchView> list() {
        return batches.findTop50ByOrderByIdDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public BatchDetail detail(Long batchId) {
        TtsBatch batch = batch(batchId);
        List<JobView> jobViews = jobs.findByBatchIdOrderBySeq(batchId).stream()
                .map(j -> new JobView(j.getId(), j.getSeq(), j.getText(), j.getStatus(), j.getRetryCnt(),
                        j.getResultMsg(), j.getDoneAt()))
                .toList();
        return new BatchDetail(view(batch), jobViews);
    }

    /** 대기 중(READY, RETRY_WAIT)인 항목을 취소. 이미 호출 중인 항목은 끝까지 처리된다. */
    @Transactional
    public int cancel(Long batchId) {
        batch(batchId);
        return jobs.cancel(batchId, TtsJobStatus.CANCELABLE, LocalDateTime.now());
    }

    /** 실패한 항목을 다시 대기열에 넣는다 (예: 크레딧 충전 후). */
    @Transactional
    public int retryFailed(Long batchId) {
        batch(batchId);
        return jobs.requeueFailed(batchId);
    }

    @Transactional(readOnly = true)
    public Path audioFile(Long batchId, Long jobId) {
        TtsJob job = jobs.findById(jobId)
                .filter(j -> j.getBatchId().equals(batchId) && j.getStatus() == TtsJobStatus.SUCCEEDED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "완료된 항목이 아닙니다."));
        return outputRoot(props).resolve(job.getFilePath());
    }

    public String format(Long batchId) {
        return batch(batchId).getFormat();
    }

    /** 완료된 오디오를 순번 순서대로 zip 으로 묶고, 순번-텍스트 목록(script.txt)을 함께 넣는다. */
    @Transactional(readOnly = true)
    public void writeZip(Long batchId, OutputStream out) throws IOException {
        batch(batchId);
        List<TtsJob> done = jobs.findByBatchIdAndStatusOrderBySeq(batchId, TtsJobStatus.SUCCEEDED);
        Path root = outputRoot(props);
        StringBuilder script = new StringBuilder();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (TtsJob job : done) {
                Path file = root.resolve(job.getFilePath());
                if (!Files.exists(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(file, zip);
                zip.closeEntry();
                script.append(name).append('\t').append(job.getText()).append('\n');
            }
            zip.putNextEntry(new ZipEntry("script.txt"));
            zip.write(script.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** 업로드 파일을 줄 단위 항목으로 나눈다. UTF-8이 아니면 윈도우 메모장 기본(MS949)으로 읽는다. */
    public static List<String> splitLines(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            text = new String(content, Charset.forName("MS949"));
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text.lines().toList();
    }

    static Path outputRoot(TypecastProperties props) {
        return Path.of(StringUtils.hasText(props.outputDir()) ? props.outputDir() : ".data");
    }

    private void clearBatchDir(Long batchId) {
        Path dir = outputRoot(props).resolve("batch-" + batchId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("이전 오디오 폴더 정리 실패: " + dir.toAbsolutePath(), e);
        }
    }

    private TtsBatch batch(Long batchId) {
        return batches.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배치가 없습니다: " + batchId));
    }

    private BatchView view(TtsBatch b) {
        Map<TtsJobStatus, Long> counts = new EnumMap<>(TtsJobStatus.class);
        for (TtsJobStatus s : TtsJobStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : jobs.countByStatus(b.getId())) {
            counts.put((TtsJobStatus) row[0], (Long) row[1]);
        }
        boolean done = TtsJobStatus.ACTIVE.stream().mapToLong(counts::get).sum() == 0;
        return new BatchView(b.getId(), b.getTitle(), b.getVoiceId(), b.getEmotion(), b.getFormat(), b.getTotal(),
                counts, done, b.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }
}
