package com.ai.api.ops.backup;

import com.ai.api.common.result.Result;
import java.io.IOException;
import java.util.Map;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据备份与容灾 REST API（add-p3-rag-feedback-ops）。
 *
 * <p>提供 MySQL JSONL 逻辑备份、ES 快照创建/恢复、binlog 配置巡检的手动触发入口； 定时任务之外供运维按需操作。
 *
 * @author add-p3-rag-feedback-ops
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsBackupController {

  private final MysqlJsonlBackupService mysqlBackupService;
  private final EsSnapshotService esSnapshotService;
  private final BinlogVerifyService binlogVerifyService;

  public OpsBackupController(
      MysqlJsonlBackupService mysqlBackupService,
      EsSnapshotService esSnapshotService,
      BinlogVerifyService binlogVerifyService) {
    this.mysqlBackupService = mysqlBackupService;
    this.esSnapshotService = esSnapshotService;
    this.binlogVerifyService = binlogVerifyService;
  }

  /** 手动触发 MySQL JSONL 逻辑备份（与每日定时任务同一逻辑）。 */
  @PostMapping("/backup/mysql")
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> backupMysql() {
    mysqlBackupService.backupDaily();
    return Result.success(null);
  }

  /** 手动触发 ES 快照（与每周定时任务同一逻辑）。 */
  @PostMapping("/backup/es/snapshot")
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> snapshotEs() {
    esSnapshotService.snapshotWeekly();
    return Result.success(null);
  }

  /** 恢复指定日期的 ES 快照（yyyyMMdd）。 */
  @PostMapping("/backup/es/restore")
  @ResponseStatus(HttpStatus.OK)
  public Result<Void> restoreEs(@RequestBody RestoreReq req) throws IOException {
    esSnapshotService.restore(req.getDate());
    return Result.success(null);
  }

  /** binlog 配置巡检。 */
  @GetMapping("/binlog/verify")
  public Result<Map<String, Object>> verifyBinlog() {
    return Result.success(binlogVerifyService.verify());
  }

  @Data
  static class RestoreReq {
    /** yyyyMMdd 格式快照日期 */
    String date;
  }
}
