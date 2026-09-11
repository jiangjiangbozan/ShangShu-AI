package com.ai.api.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * KB 命中日志实体（add-customer-service）。
 *
 * <p>记录每次 KB 检索的命中情况，用于运营分析与命中率统计。
 *
 * @author add-customer-service
 */
@Data
@TableName("cs_kb_hit_log")
public class CsKbHitLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long runtimeId;
  private String hitQuery;
  private Long kbId;
  private String docId;
  private BigDecimal score;
  private Integer hit;
  private LocalDateTime createTime;
}
