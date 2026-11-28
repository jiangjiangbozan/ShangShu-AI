package com.ai.api.cs.mapper;

import com.ai.api.cs.entity.CsSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 客服会话扩展 Mapper（add-customer-service）。
 *
 * @author add-customer-service
 */
@Mapper
public interface CsSessionMapper extends BaseMapper<CsSession> {

  /** 按接管人过滤。 */
  @Select("SELECT * FROM cs_session WHERE assigned_to = #{userId} ORDER BY update_time DESC")
  List<CsSession> selectByAssignedTo(@Param("userId") Long userId);

  /** 按 session_id 唯一查询。 */
  @Select("SELECT * FROM cs_session WHERE session_id = #{sessionId}")
  CsSession selectBySessionId(@Param("sessionId") Long sessionId);

  /** 按状态过滤（status 可为 ai/human/done/failed）。 */
  @Select("SELECT * FROM cs_session WHERE status = #{status} ORDER BY update_time DESC")
  List<CsSession> selectByStatus(@Param("status") String status);

  /** 接管：更新状态 + 接管人。 */
  @Update(
      "UPDATE cs_session SET status = 'human', assigned_to = #{userId}, "
          + "update_time = NOW() WHERE session_id = #{sessionId}")
  int takeover(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

  /**
   * 带数据范围的接管（mobile-p0）：管理员可接管任意会话，客服仅能接管无归属或本人的会话。
   *
   * <p>范围条件下推到 UPDATE 的 WHERE，避免"先查后改"的竞态越权。
   */
  @Update({
    "<script>",
    "UPDATE cs_session SET status = 'human', assigned_to = #{userId}, update_time = NOW()",
    "WHERE session_id = #{sessionId}",
    "AND (",
    "<if test='admin'>1 = 1 OR </if>",
    "assigned_to IS NULL OR assigned_to = #{userId}",
    ")",
    "</script>"
  })
  int takeoverScoped(
      @Param("sessionId") Long sessionId,
      @Param("userId") Long userId,
      @Param("admin") boolean admin);
}
