package com.example.voiceagent.controller;

import com.example.voiceagent.service.VoiceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*")

public class AudioController {

    @Value("${voiceagent.mode}")
    private String mode;

    private final VoiceService voiceService;

    public AudioController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @PostMapping("/process")
    public ResponseEntity<byte[]> processAudio(@RequestBody byte[] audioBytes) {
        byte[] result = mode.equals("mock")
                ? voiceService.mockResponse()
                : voiceService.processWithOpenAI(audioBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .body(result);
    }

}
