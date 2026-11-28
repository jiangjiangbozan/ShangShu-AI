package com.ai.api.knowledge.validation;

import com.ai.api.common.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库上传文件的边界校验器（mobile-p0）。
 *
 * <p>在 Controller 入口校验扩展名、声明的 MIME 与文件大小，非法输入以 400 返回， 避免非法文件名写入磁盘或落库时才失败。 大小上限直接读取 Spring
 * Multipart 配置，避免与容器限制不一致。
 *
 * @author mobile-p0
 */
@Component
public class FileUploadValidator {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of(
          "application/msword",
          "application/octet-stream",
          "application/pdf",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "text/html",
          "text/markdown",
          "text/plain");
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of("doc", "docx", "htm", "html", "md", "pdf", "txt");
  private static final long MIN_FILE_BYTES = 1L;

  private final DataSize maxFileSize;

  public FileUploadValidator(
      @Value("${spring.servlet.multipart.max-file-size:16MB}") DataSize maxFileSize) {
    this.maxFileSize = maxFileSize;
  }

  /**
   * 校验单个上传文件。
   *
   * @param file 上传文件
   */
  public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("上传文件不能为空");
    }
    String extension = extractExtension(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BadRequestException("不支持的文件扩展名: " + extension);
    }
    validateContentType(file.getContentType());
    if (file.getSize() < MIN_FILE_BYTES || file.getSize() > maxFileSize.toBytes()) {
      throw new BadRequestException("文件大小必须在 " + MIN_FILE_BYTES + "B 到 " + maxFileSize + " 之间");
    }
  }

  /**
   * 批量校验：任一文件非法则整体拒绝。
   *
   * @param files 上传文件列表
   */
  public void validateAll(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new BadRequestException("文件列表不能为空");
    }
    files.forEach(this::validate);
  }

  /**
   * 提取小写扩展名。
   *
   * @param originalName 原始文件名
   * @return 扩展名
   */
  private String extractExtension(String originalName) {
    if (originalName == null || !originalName.contains(".")) {
      throw new BadRequestException("文件名必须包含扩展名");
    }
    return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  /**
   * 校验声明的 MIME；txt/md 浏览器常上报通用类型，按扩展名兜底。
   *
   * @param contentType 请求声明的 Content-Type
   */
  private void validateContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return;
    }
    String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
      throw new BadRequestException("不支持的文件 MIME 类型: " + normalized);
    }
  }
}
