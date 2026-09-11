package com.ai.api.voice.controller;

import com.ai.api.common.result.Result;
import com.ai.api.voice.service.VoiceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

  private final VoiceService voiceService;

  /** STT：音频转文字 接受 audio/webm, audio/mp3, audio/wav 等格式 */
  @PostMapping("/stt")
  public Result<Map<String, String>> speechToText(@RequestParam("file") MultipartFile file) {
    String text = voiceService.speechToText(file);
    return Result.success(Map.of("text", text));
  }

  /** TTS：文字转语音 返回 MP3 音频流 */
  @PostMapping("/tts")
  public ResponseEntity<byte[]> textToSpeech(@RequestBody Map<String, String> request) {
    String text = request.getOrDefault("text", "");
    String voice = request.getOrDefault("voice", "alloy");

    byte[] audio = voiceService.textToSpeech(text, voice);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
    headers.setContentLength(audio.length);
    headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"speech.mp3\"");

    return ResponseEntity.ok().headers(headers).body(audio);
  }
}
