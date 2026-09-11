package com.ai.api.cs.mapper;

import com.ai.api.cs.entity.CsLabel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客服标注 Mapper（add-customer-service）。
 *
 * @author add-customer-service
 */
@Mapper
public interface CsLabelMapper extends BaseMapper<CsLabel> {

  /** 按会话查询所有标注。 */
  @Select("SELECT * FROM cs_label WHERE session_id = #{sessionId} ORDER BY create_time DESC")
  List<CsLabel> selectBySessionId(@Param("sessionId") Long sessionId);
}
