package com.ai.api.voice.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.model.config.ModelConfig;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 语音服务：STT（语音转文字）+ TTS（文字转语音） 使用 OpenAI 兼容协议调用 Whisper / TTS API */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

  private final ModelConfig modelConfig;
  private final RestTemplate restTemplate = new RestTemplate();

  /** STT：音频转文字 调用 OpenAI 兼容的 /v1/audio/transcriptions 端点 */
  public String speechToText(MultipartFile audioFile) {
    try {
      String baseUrl = resolveBaseUrl();
      String apiKey = resolveApiKey();
      String url = baseUrl + "/v1/audio/transcriptions";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      headers.setBearerAuth(apiKey);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add(
          "file",
          new ByteArrayResource(audioFile.getBytes()) {
            @Override
            public String getFilename() {
              return audioFile.getOriginalFilename() != null
                  ? audioFile.getOriginalFilename()
                  : "audio.webm";
            }
          });
      body.add("model", "whisper-1");

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
      ResponseEntity<SttResponse> response =
          restTemplate.exchange(url, HttpMethod.POST, requestEntity, SttResponse.class);

      if (response.getBody() != null && response.getBody().getText() != null) {
        return response.getBody().getText();
      }
      throw new BusinessException(ErrorCode.VOICE_STT_FAILED);
    } catch (IOException e) {
      log.error("STT 文件读取失败", e);
      throw new BusinessException(ErrorCode.VOICE_STT_FAILED);
    } catch (Exception e) {
      if (e instanceof BusinessException) throw (BusinessException) e;
      log.error("STT 调用失败", e);
      throw new BusinessException(ErrorCode.VOICE_STT_FAILED);
    }
  }

  /** TTS：文字转语音 调用 OpenAI 兼容的 /v1/audio/speech 端点 */
  public byte[] textToSpeech(String text, String voice) {
    try {
      String baseUrl = resolveBaseUrl();
      String apiKey = resolveApiKey();
      String url = baseUrl + "/v1/audio/speech";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(apiKey);

      TtsRequest requestBody = new TtsRequest();
      requestBody.setModel("tts-1");
      requestBody.setInput(text.length() > 4096 ? text.substring(0, 4096) : text);
      requestBody.setVoice(voice != null ? voice : "alloy");
      requestBody.setResponseFormat("mp3");

      HttpEntity<TtsRequest> requestEntity = new HttpEntity<>(requestBody, headers);
      ResponseEntity<byte[]> response =
          restTemplate.exchange(url, HttpMethod.POST, requestEntity, byte[].class);

      if (response.getBody() != null && response.getBody().length > 0) {
        return response.getBody();
      }
      throw new BusinessException(ErrorCode.VOICE_TTS_FAILED);
    } catch (Exception e) {
      if (e instanceof BusinessException) throw (BusinessException) e;
      log.error("TTS 调用失败", e);
      throw new BusinessException(ErrorCode.VOICE_TTS_FAILED);
    }
  }

  private String resolveBaseUrl() {
    ModelConfig.ProviderConfig ds = modelConfig.getDeepSeek();
    if (ds != null && ds.getEnabled() && ds.getBaseUrl() != null) {
      return ds.getBaseUrl();
    }
    ModelConfig.ProviderConfig qw = modelConfig.getQwen();
    if (qw != null && qw.getEnabled() && qw.getBaseUrl() != null) {
      return qw.getBaseUrl();
    }
    return "https://api.openai.com";
  }

  private String resolveApiKey() {
    ModelConfig.ProviderConfig ds = modelConfig.getDeepSeek();
    if (ds != null && ds.getApiKey() != null && !ds.getApiKey().isBlank()) {
      return ds.getApiKey();
    }
    ModelConfig.ProviderConfig qw = modelConfig.getQwen();
    if (qw != null && qw.getApiKey() != null && !qw.getApiKey().isBlank()) {
      return qw.getApiKey();
    }
    throw new BusinessException(ErrorCode.VOICE_STT_FAILED);
  }

  // ---- inner DTOs ----

  @lombok.Data
  public static class SttResponse {
    private String text;
  }

  @lombok.Data
  public static class TtsRequest {
    private String model;
    private String input;
    private String voice;
    private String responseFormat;
  }
}
