package com.ai.api.audit.service;

import com.ai.api.audit.entity.OperationLog;
import com.ai.api.audit.mapper.OperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 操作审计日志（A6）：记录、分页查询、清理。 */
@Slf4j
@Service
public class OperationLogService {

  private final OperationLogMapper operationLogMapper;

  public OperationLogService(OperationLogMapper operationLogMapper) {
    this.operationLogMapper = operationLogMapper;
  }

  /** 记录一条操作日志（切面调用；失败不影响业务）。 */
  public void record(OperationLog entry) {
    try {
      if (entry.getParams() != null && entry.getParams().length() > 2000) {
        entry.setParams(entry.getParams().substring(0, 2000));
      }
      if (entry.getErrorMsg() != null && entry.getErrorMsg().length() > 512) {
        entry.setErrorMsg(entry.getErrorMsg().substring(0, 512));
      }
      operationLogMapper.insert(entry);
    } catch (Exception e) {
      log.warn("[audit][record 失败忽略: {}]", e.getMessage());
    }
  }

  public IPage<OperationLog> list(
      int page,
      int size,
      String username,
      String module,
      String action,
      String status,
      LocalDateTime start,
      LocalDateTime end) {
    LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .like(username != null && !username.isBlank(), OperationLog::getUsername, username)
        .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
        .like(action != null && !action.isBlank(), OperationLog::getAction, action)
        .eq(status != null && !status.isBlank(), OperationLog::getStatus, status)
        .ge(start != null, OperationLog::getCreateTime, start)
        .le(end != null, OperationLog::getCreateTime, end)
        .orderByDesc(OperationLog::getCreateTime);
    return operationLogMapper.selectPage(Page.of(page, size), wrapper);
  }

  /** 清理指定天数之前的日志。 */
  public int cleanBefore(int days) {
    if (days <= 0) {
      throw new IllegalArgumentException("天数必须大于 0");
    }
    LocalDateTime before = LocalDateTime.now().minusDays(days);
    return operationLogMapper.delete(
        new LambdaQueryWrapper<OperationLog>()
            .lt(OperationLog::getCreateTime, before)
            // 逻辑删除避免清空索引；如需物理删除请改用硬删除
            .last("LIMIT 1000"));
  }
}
