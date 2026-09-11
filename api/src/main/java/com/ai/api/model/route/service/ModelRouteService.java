package com.ai.api.model.route.service;

import com.ai.api.model.route.entity.ModelRoutePolicy;
import com.ai.api.model.route.mapper.ModelRoutePolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 模型路由 / 降级策略（A5）。 */
@Slf4j
@Service
public class ModelRouteService {

  private final ModelRoutePolicyMapper policyMapper;

  public ModelRouteService(ModelRoutePolicyMapper policyMapper) {
    this.policyMapper = policyMapper;
  }

  // ==================== CRUD ====================

  public IPage<ModelRoutePolicy> list(int page, int size, String modelId) {
    LambdaQueryWrapper<ModelRoutePolicy> wrapper = new LambdaQueryWrapper<>();
    if (modelId != null && !modelId.isBlank()) {
      wrapper.like(ModelRoutePolicy::getModelId, modelId);
    }
    wrapper.orderByAsc(ModelRoutePolicy::getModelId).orderByAsc(ModelRoutePolicy::getPriority);
    return policyMapper.selectPage(Page.of(page, size), wrapper);
  }

  public ModelRoutePolicy create(ModelRoutePolicy policy, String creator) {
    if (policy.getModelId() == null || policy.getModelId().isBlank()) {
      throw new IllegalArgumentException("主模型 ID 不能为空");
    }
    if (policy.getFallbackModelId() == null || policy.getFallbackModelId().isBlank()) {
      throw new IllegalArgumentException("降级模型 ID 不能为空");
    }
    if (policy.getModelId().equals(policy.getFallbackModelId())) {
      throw new IllegalArgumentException("降级模型不能与主模型相同");
    }
    if (policy.getPriority() == null) policy.setPriority(10);
    if (policy.getStatus() == null) policy.setStatus("active");
    policy.setCreator(creator);
    policyMapper.insert(policy);
    return policy;
  }

  public ModelRoutePolicy update(Long id, ModelRoutePolicy policy, String updater) {
    ModelRoutePolicy exist = policyMapper.selectById(id);
    if (exist == null) {
      throw new IllegalArgumentException("策略不存在: " + id);
    }
    policy.setId(id);
    policy.setUpdater(updater);
    policyMapper.updateById(policy);
    return policyMapper.selectById(id);
  }

  public void delete(Long id) {
    ModelRoutePolicy exist = policyMapper.selectById(id);
    if (exist == null) {
      throw new IllegalArgumentException("策略不存在: " + id);
    }
    policyMapper.deleteById(id);
  }

  // ==================== 执行侧 ====================

  /**
   * 返回 modelId 的降级模型链（按 priority 升序，自动去重并排除自身）。
   *
   * @param modelId 主模型 ID
   * @return 降级模型列表（不含主模型）
   */
  public List<String> resolveFallbackChain(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      return List.of();
    }
    List<ModelRoutePolicy> policies =
        policyMapper.selectList(
            new LambdaQueryWrapper<ModelRoutePolicy>()
                .eq(ModelRoutePolicy::getModelId, modelId)
                .eq(ModelRoutePolicy::getStatus, "active")
                .orderByAsc(ModelRoutePolicy::getPriority));
    Set<String> chain = new LinkedHashSet<>();
    for (ModelRoutePolicy p : policies) {
      if (p.getFallbackModelId() != null
          && !p.getFallbackModelId().isBlank()
          && !p.getFallbackModelId().equals(modelId)) {
        chain.add(p.getFallbackModelId());
      }
    }
    return List.copyOf(chain);
  }
}
