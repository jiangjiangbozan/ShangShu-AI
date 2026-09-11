package com.ai.api.cs.mapper;

import com.ai.api.cs.entity.CsClarifyTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客服澄清话术模板 Mapper（add-customer-service）。
 *
 * @author add-customer-service
 */
@Mapper
public interface CsClarifyTemplateMapper extends BaseMapper<CsClarifyTemplate> {

  /** 按意图查询所有槽位的澄清模板。 */
  @Select("SELECT * FROM cs_clarify_template WHERE intent = #{intent}")
  List<CsClarifyTemplate> selectByIntent(@Param("intent") String intent);

  /** 按意图 + 槽位查询单条模板。 */
  @Select("SELECT * FROM cs_clarify_template WHERE intent = #{intent} AND slot_name = #{slotName}")
  CsClarifyTemplate selectByIntentAndSlot(
      @Param("intent") String intent, @Param("slotName") String slotName);
}
