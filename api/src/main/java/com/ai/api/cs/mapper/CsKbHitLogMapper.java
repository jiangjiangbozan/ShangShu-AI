package com.ai.api.cs.mapper;

import com.ai.api.cs.entity.CsKbHitLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * KB 命中日志 Mapper（add-customer-service）。
 *
 * @author add-customer-service
 */
@Mapper
public interface CsKbHitLogMapper extends BaseMapper<CsKbHitLog> {

  /** 按天统计命中率，返回 [{day, total, hit, hit_rate}]。 */
  @Select(
      "SELECT DATE(create_time) AS day, COUNT(*) AS total, "
          + "SUM(CASE WHEN hit = 1 THEN 1 ELSE 0 END) AS hit, "
          + "ROUND(SUM(CASE WHEN hit = 1 THEN 1 ELSE 0 END) / COUNT(*), 4) AS hit_rate "
          + "FROM cs_kb_hit_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
          + "GROUP BY DATE(create_time) ORDER BY day ASC")
  List<Map<String, Object>> selectStatsByDays(@Param("days") int days);
}
