package com.ai.api.ops.backup;

import com.ai.api.chat.mapper.ChatMessageMapper;
import com.ai.api.chat.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * MySQL 逻辑备份服务（add-p3-rag-feedback-ops）。
 *
 * <p>每日 03:00 导出 {@code chat_session} 与 {@code chat_message_log} 表为 JSONL 文件，保留 7 天。
 *
 * <p>设计为 JSONL 而非 SQL dump：便于按行流式读取与部分恢复，避免整库导入耗时。
 *
 * <p>失败仅记录 ERROR 不影响主流程。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class MysqlJsonlBackupService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final ChatSessionMapper sessionMapper;
  private final ChatMessageMapper messageMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${backup.mysql.path:/data/backup/mysql/}")
  private String backupPath;

  @Value("${backup.mysql.retention-days:7}")
  private int retentionDays;

  public MysqlJsonlBackupService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
    this.sessionMapper = sessionMapper;
    this.messageMapper = messageMapper;
  }

  /** 每日 03:00 触发逻辑备份。 */
  @Scheduled(cron = "${backup.mysql.cron:0 0 3 * * ?}")
  public void backupDaily() {
    String date = LocalDate.now().format(DATE_FMT);
    try {
      Files.createDirectories(Paths.get(backupPath));
      exportTable("chat_session", date, () -> sessionMapper.selectList(new QueryWrapper<>()));
      exportTable("chat_message_log", date, () -> messageMapper.selectList(new QueryWrapper<>()));
      purgeExpired();
      log.info("[MysqlJsonlBackupService][备份完成: date={}]", date);
    } catch (Exception e) {
      log.error("[MysqlJsonlBackupService][备份失败: date={}, error={}]", date, e.getMessage(), e);
    }
  }

  private <T> void exportTable(
      String table, String date, java.util.function.Supplier<List<T>> loader) throws IOException {
    Path file = Paths.get(backupPath, table + "_" + date + ".jsonl");
    List<T> rows = loader.get();
    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      for (T row : rows) {
        writer.write(objectMapper.writeValueAsString(row));
        writer.newLine();
      }
    }
    log.info(
        "[MysqlJsonlBackupService][导出完成: table={}, rows={}, file={}]", table, rows.size(), file);
  }

  /** 清理 7 天前的 JSONL 文件。 */
  private void purgeExpired() throws IOException {
    Path dir = Paths.get(backupPath);
    if (!Files.exists(dir)) {
      return;
    }
    LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
    List<Path> deleted =
        Files.list(dir)
            .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
            .filter(
                p -> {
                  String name = p.getFileName().toString();
                  String datePart = extractDatePart(name);
                  if (datePart == null) return false;
                  try {
                    return LocalDate.parse(datePart, DATE_FMT).isBefore(cutoff);
                  } catch (Exception e) {
                    return false;
                  }
                })
            .toList();
    for (Path p : deleted) {
      Files.deleteIfExists(p);
      log.info("[MysqlJsonlBackupService][清理过期备份: file={}]", p);
    }
  }

  private String extractDatePart(String name) {
    int us = name.lastIndexOf('_');
    int dot = name.lastIndexOf('.');
    if (us < 0 || dot < 0 || dot <= us) return null;
    return name.substring(us + 1, dot);
  }
}
