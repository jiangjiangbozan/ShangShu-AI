package com.ai.api.ops.backup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * MySQL binlog 配置校验服务（add-p3-rag-feedback-ops）。
 *
 * <p>校验 binlog 是否开启、ROW 模式、保留天数，并列出当前 binlog 文件，用于运维巡检接口。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class BinlogVerifyService {

  private final JdbcTemplate jdbcTemplate;

  public BinlogVerifyService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 校验 binlog 配置状态。
   *
   * @return log_bin/binlog_format/binlog_row_image/binlog_expire_logs_seconds 与 binlog 文件列表
   */
  public Map<String, Object> verify() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("log_bin", showVariable("log_bin"));
    result.put("binlog_format", showVariable("binlog_format"));
    result.put("binlog_row_image", showVariable("binlog_row_image"));
    result.put("binlog_expire_logs_seconds", showVariable("binlog_expire_logs_seconds"));
    try {
      List<Map<String, Object>> binaryLogs = jdbcTemplate.queryForList("SHOW BINARY LOGS");
      result.put("binary_logs", binaryLogs);
      result.put("binary_log_count", binaryLogs.size());
    } catch (Exception e) {
      log.warn("[BinlogVerifyService][SHOW BINARY LOGS 失败: error={}]", e.getMessage());
      result.put("binary_logs", List.of());
      result.put("binary_log_error", e.getMessage());
    }
    boolean ok =
        "ON".equalsIgnoreCase(String.valueOf(result.get("log_bin")))
            && "ROW".equalsIgnoreCase(String.valueOf(result.get("binlog_format")));
    result.put("compliant", ok);
    return result;
  }

  private Object showVariable(String name) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SHOW VARIABLES LIKE '" + name + "'");
    if (rows.isEmpty()) {
      return null;
    }
    return rows.get(0).get("Value");
  }
}
