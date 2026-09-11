package com.ai.api.prompt.service;

import static com.ai.api.common.exception.ErrorCode.COMMON_BAD_PARAM;
import static com.ai.api.common.exception.ErrorCode.COMMON_NOT_FOUND;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.result.PageResult;
import com.ai.api.prompt.dto.PromptTemplateReqDTO;
import com.ai.api.prompt.dto.PromptTemplateRespDTO;
import com.ai.api.prompt.entity.PromptTemplate;
import com.ai.api.prompt.mapper.PromptTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromptTemplateService {

  private final PromptTemplateMapper mapper;
  private static final ObjectMapper JSON = new ObjectMapper();

  public PageResult<PromptTemplateRespDTO> list(
      String keyword, String category, int page, int size) {
    LambdaQueryWrapper<PromptTemplate> w = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.isBlank()) {
      w.and(
          c ->
              c.like(PromptTemplate::getName, keyword)
                  .or()
                  .like(PromptTemplate::getDescription, keyword));
    }
    if (category != null && !category.isBlank()) {
      w.eq(PromptTemplate::getCategory, category);
    }
    w.orderByDesc(PromptTemplate::getUpdateTime);
    Page<PromptTemplate> result = mapper.selectPage(new Page<>(page, size), w);
    List<PromptTemplateRespDTO> list = result.getRecords().stream().map(this::toResp).toList();
    return PageResult.of(list, result.getTotal());
  }

  public PromptTemplateRespDTO get(Long id) {
    PromptTemplate t = mapper.selectById(id);
    if (t == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "模板不存在");
    }
    return toResp(t);
  }

  @Transactional
  public PromptTemplateRespDTO create(PromptTemplateReqDTO req, String username) {
    PromptTemplate t = toEntity(req);
    t.setCreator(username);
    t.setVersion(1);
    mapper.insert(t);
    return toResp(t);
  }

  @Transactional
  public PromptTemplateRespDTO update(Long id, PromptTemplateReqDTO req) {
    PromptTemplate t = mapper.selectById(id);
    if (t == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "模板不存在");
    }
    t.setName(req.getName());
    t.setCategory(req.getCategory());
    t.setContent(req.getContent());
    t.setVariables(toJson(req.getVariables()));
    t.setModelId(req.getModelId());
    t.setIsActive(req.getIsActive());
    t.setDescription(req.getDescription());
    mapper.updateById(t);
    return toResp(t);
  }

  @Transactional
  public PromptTemplateRespDTO createNewVersion(Long id, String username) {
    PromptTemplate existing = mapper.selectById(id);
    if (existing == null) {
      throw new BusinessException(COMMON_NOT_FOUND, "模板不存在");
    }
    LambdaQueryWrapper<PromptTemplate> w = new LambdaQueryWrapper<>();
    w.eq(PromptTemplate::getName, existing.getName())
        .orderByDesc(PromptTemplate::getVersion)
        .last("LIMIT 1");
    PromptTemplate latest = mapper.selectOne(w);
    int nextVersion = (latest != null ? latest.getVersion() : 0) + 1;

    PromptTemplate newVer = new PromptTemplate();
    newVer.setName(existing.getName());
    newVer.setCategory(existing.getCategory());
    newVer.setContent(existing.getContent());
    newVer.setVariables(existing.getVariables());
    newVer.setModelId(existing.getModelId());
    newVer.setIsActive(true);
    newVer.setVersion(nextVersion);
    newVer.setDescription(existing.getDescription());
    newVer.setCreator(username);
    mapper.insert(newVer);
    return toResp(newVer);
  }

  @Transactional
  public void delete(Long id) {
    mapper.deleteById(id);
  }

  private PromptTemplateRespDTO toResp(PromptTemplate t) {
    PromptTemplateRespDTO r = new PromptTemplateRespDTO();
    r.setId(t.getId());
    r.setName(t.getName());
    r.setCategory(t.getCategory());
    r.setContent(t.getContent());
    r.setVariables(fromJson(t.getVariables()));
    r.setModelId(t.getModelId());
    r.setIsActive(t.getIsActive());
    r.setVersion(t.getVersion());
    r.setDescription(t.getDescription());
    r.setCreatedBy(t.getCreator());
    r.setCreatedAt(t.getCreateTime());
    r.setUpdatedAt(t.getUpdateTime());
    return r;
  }

  private PromptTemplate toEntity(PromptTemplateReqDTO req) {
    PromptTemplate t = new PromptTemplate();
    t.setName(req.getName());
    t.setCategory(req.getCategory() != null ? req.getCategory() : "chat");
    t.setContent(req.getContent());
    t.setVariables(toJson(req.getVariables()));
    t.setModelId(req.getModelId());
    t.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
    t.setDescription(req.getDescription());
    return t;
  }

  private String toJson(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return JSON.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new BusinessException(COMMON_BAD_PARAM, "变量序列化失败");
    }
  }

  private List<String> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return JSON.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }
}
