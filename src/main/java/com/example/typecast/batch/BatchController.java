package com.example.typecast.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService service;
    private final TtsWorkers workers;
    private final QueueProperties queue;

    public BatchController(BatchService service, TtsWorkers workers, QueueProperties queue) {
        this.service = service;
        this.workers = workers;
        this.queue = queue;
    }

    /** JSON 으로 항목 목록 제출: {"title":..,"emotion":..,"format":"mp3","items":["문장1","문장2"]} */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BatchService.BatchView submit(@RequestBody BatchService.Submit req) {
        BatchService.BatchView view = service.submit(req);
        workers.wakeUp();
        return view;
    }

    /** 파일 업로드. 엑셀(.xlsx/.xls)은 한 행 = 한 항목, 텍스트 파일은 한 줄 = 한 항목 (빈 항목은 무시) */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchService.BatchView upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) String voiceId,
                                         @RequestParam(required = false) String language,
                                         @RequestParam(required = false) String emotion,
                                         @RequestParam(required = false) Double intensity,
                                         @RequestParam(required = false) String format) throws IOException {
        List<String> lines = ExcelItems.isExcel(file.getOriginalFilename())
                ? ExcelItems.read(file.getBytes())
                : BatchService.splitLines(file.getBytes());
        String batchTitle = title != null && !title.isBlank() ? title : file.getOriginalFilename();
        BatchService.BatchView view = service.submit(
                new BatchService.Submit(batchTitle, voiceId, language, emotion, intensity, format, lines));
        workers.wakeUp();
        return view;
    }

    /** 엑셀 업로드 양식 다운로드 */
    @GetMapping("/template.xlsx")
    public ResponseEntity<StreamingResponseBody> template() {
        StreamingResponseBody body = out -> ExcelItems.writeTemplate(out, queue.maxItems());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("tts-batch-template.xlsx").build().toString())
                .body(body);
    }

    @GetMapping
    public List<BatchService.BatchView> list() {
        return service.list();
    }

    @GetMapping("/{batchId}")
    public BatchService.BatchDetail detail(@PathVariable Long batchId) {
        return service.detail(batchId);
    }

    @PostMapping("/{batchId}/cancel")
    public Map<String, Integer> cancel(@PathVariable Long batchId) {
        return Map.of("canceled", service.cancel(batchId));
    }

    @PostMapping("/{batchId}/retry-failed")
    public Map<String, Integer> retryFailed(@PathVariable Long batchId) {
        int requeued = service.retryFailed(batchId);
        workers.wakeUp();
        return Map.of("requeued", requeued);
    }

    @GetMapping("/{batchId}/jobs/{jobId}/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long batchId, @PathVariable Long jobId) {
        Path file = service.audioFile(batchId, jobId);
        String format = service.format(batchId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("mp3".equals(format) ? "audio/mpeg" : "audio/wav"))
                .body(new FileSystemResource(file));
    }

    /** 완료된 파일 전체를 zip 으로 다운로드 (0001.mp3, 0002.mp3, ... + script.txt) */
    @GetMapping("/{batchId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long batchId) {
        service.format(batchId); // 없는 배치면 404
        StreamingResponseBody body = out -> service.writeZip(batchId, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("batch-" + batchId + ".zip").build().toString())
                .body(body);
    }
}
