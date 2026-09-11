package com.ai.api.chat.annotation.service;

import com.ai.api.chat.annotation.entity.Annotation;
import com.ai.api.chat.annotation.mapper.AnnotationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 标注问答管理服务 */
@Service
@RequiredArgsConstructor
public class AnnotationService {

  private final AnnotationMapper annotationMapper;

  /** 分页查询（支持关键词搜索） */
  public Page<Annotation> list(String keyword, int page, int size) {
    QueryWrapper<Annotation> qw = new QueryWrapper<Annotation>().orderByDesc("create_time");
    if (keyword != null && !keyword.isBlank()) {
      qw.and(w -> w.like("question", keyword).or().like("answer", keyword));
    }
    return annotationMapper.selectPage(new Page<>(page, size), qw);
  }

  /** 新增标注 */
  public Annotation create(String question, String answer, Long messageId) {
    Annotation entity = new Annotation();
    entity.setQuestion(question);
    entity.setAnswer(answer);
    entity.setMessageId(messageId);
    entity.setHitCount(0);
    entity.setEnabled(1);
    annotationMapper.insert(entity);
    return entity;
  }

  /** 编辑标注 */
  public Annotation update(Long id, String question, String answer) {
    Annotation entity = annotationMapper.selectById(id);
    if (entity == null) throw new IllegalArgumentException("标注不存在");
    if (question != null) entity.setQuestion(question);
    if (answer != null) entity.setAnswer(answer);
    annotationMapper.updateById(entity);
    return entity;
  }

  /** 删除标注 */
  public void delete(Long id) {
    annotationMapper.deleteById(id);
  }

  /** 批量删除 */
  public void deleteBatch(java.util.List<Long> ids) {
    annotationMapper.deleteByIds(ids);
  }

  /** 启用/禁用 */
  public void toggleEnabled(Long id, boolean enabled) {
    Annotation entity = annotationMapper.selectById(id);
    if (entity == null) throw new IllegalArgumentException("标注不存在");
    entity.setEnabled(enabled ? 1 : 0);
    annotationMapper.updateById(entity);
  }

  /** 增加命中次数 */
  public void incrementHitCount(Long id) {
    Annotation entity = annotationMapper.selectById(id);
    if (entity != null) {
      entity.setHitCount((entity.getHitCount() == null ? 0 : entity.getHitCount()) + 1);
      annotationMapper.updateById(entity);
    }
  }

  /** 根据 ID 获取 */
  public Annotation getById(Long id) {
    return annotationMapper.selectById(id);
  }
}
