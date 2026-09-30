package com.example.typecast.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.example.typecast.client.TtsRequest;
import com.example.typecast.client.TypecastClient;
import com.example.typecast.client.TypecastProperties;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TtsController {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final TypecastClient typecastClient;
    private final TypecastProperties props;

    public TtsController(TypecastClient typecastClient, TypecastProperties props) {
        this.typecastClient = typecastClient;
        this.props = props;
    }

    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody SpeakRequest req) {
        String format = "mp3".equalsIgnoreCase(req.format()) ? "mp3" : "wav";

        TtsRequest.Prompt prompt = StringUtils.hasText(req.emotion())
                ? TtsRequest.Prompt.preset(req.emotion(), req.intensity())
                : TtsRequest.Prompt.smart(null, null);

        TtsRequest request = new TtsRequest(
                req.text(),
                StringUtils.hasText(req.voiceId()) ? req.voiceId() : props.defaultVoiceId(),
                props.model(),
                req.language(),
                prompt,
                new TtsRequest.Output(null, req.pitch(), req.tempo(), format),
                null
        );

        byte[] audio = typecastClient.textToSpeech(request);
        String filename = "tts_" + LocalDateTime.now().format(FILE_TS) + "." + format;
        save(filename, audio);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("mp3".equals(format) ? "audio/mpeg" : "audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(audio);
    }

    /** 생성된 오디오를 output-dir(기본 .data)에 파일로 저장한다. */
    private void save(String filename, byte[] audio) {
        Path dir = Path.of(StringUtils.hasText(props.outputDir()) ? props.outputDir() : ".data");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), audio);
        } catch (IOException e) {
            throw new UncheckedIOException("오디오 파일 저장 실패: " + dir.toAbsolutePath(), e);
        }
    }

    @GetMapping(value = "/voices", produces = MediaType.APPLICATION_JSON_VALUE)
    public String voices(@RequestParam(required = false) String gender) {
        return typecastClient.voices(props.model(), gender);
    }

    @GetMapping(value = "/subscription", produces = MediaType.APPLICATION_JSON_VALUE)
    public String subscription() {
        return typecastClient.subscription();
    }
}
