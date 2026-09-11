package com.ai.api.agent.mapper;

import com.ai.api.agent.entity.SkillDefinition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Skill 技能元数据 Mapper（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@Mapper
public interface SkillDefinitionMapper extends BaseMapper<SkillDefinition> {

  /** 按名称查询最新版本（version 降序）。 */
  @Select(
      "SELECT * FROM skill_definition WHERE name = #{name} AND enabled = 1 "
          + "ORDER BY version DESC LIMIT 1")
  SkillDefinition selectLatestByName(@Param("name") String name);

  /** 按名称 + 版本精确查询。 */
  @Select(
      "SELECT * FROM skill_definition WHERE name = #{name} AND version = #{version} "
          + "AND enabled = 1 LIMIT 1")
  SkillDefinition selectByNameAndVersion(
      @Param("name") String name, @Param("version") String version);
}
