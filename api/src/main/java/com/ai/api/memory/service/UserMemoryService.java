package com.ai.api.memory.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.memory.dto.MemoryCreateReqDTO;
import com.ai.api.memory.dto.MemoryRespDTO;
import com.ai.api.memory.entity.UserMemory;
import com.ai.api.memory.mapper.UserMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户长期记忆服务
 *
 * <p>提供记忆的 CRUD 和搜索功能。记忆按用户隔离，支持分类筛选和关键词搜索。
 */
@Service
@RequiredArgsConstructor
public class UserMemoryService {

  private final UserMemoryMapper memoryMapper;

  /** 创建记忆 */
  public MemoryRespDTO createMemory(Long userId, MemoryCreateReqDTO req) {
    UserMemory memory = new UserMemory();
    memory.setUserId(userId);
    memory.setCategory(req.getCategory());
    memory.setContent(req.getContent());
    memory.setSource(req.getSource());
    memory.setImportance(req.getImportance());
    memory.setTimesUsed(0);
    memoryMapper.insert(memory);
    return toDTO(memory);
  }

  /** 查询用户记忆列表（分页） */
  public List<MemoryRespDTO> listMemories(Long userId, String category, int page, int size) {
    LambdaQueryWrapper<UserMemory> wrapper =
        new LambdaQueryWrapper<UserMemory>()
            .eq(UserMemory::getUserId, userId)
            .eq(category != null && !category.isBlank(), UserMemory::getCategory, category)
            .orderByDesc(UserMemory::getImportance)
            .orderByDesc(UserMemory::getCreateTime);

    Page<UserMemory> pageResult = memoryMapper.selectPage(new Page<>(page, size), wrapper);
    return pageResult.getRecords().stream().map(this::toDTO).toList();
  }

  /** 搜索记忆（按关键词模糊匹配） */
  public List<MemoryRespDTO> searchMemories(Long userId, String keyword, int size) {
    LambdaQueryWrapper<UserMemory> wrapper =
        new LambdaQueryWrapper<UserMemory>()
            .eq(UserMemory::getUserId, userId)
            .like(UserMemory::getContent, keyword)
            .orderByDesc(UserMemory::getImportance)
            .orderByDesc(UserMemory::getCreateTime)
            .last("LIMIT " + size);

    return memoryMapper.selectList(wrapper).stream().map(this::toDTO).toList();
  }

  /** 删除记忆 */
  public void deleteMemory(Long userId, Long memoryId) {
    UserMemory memory = memoryMapper.selectById(memoryId);
    if (memory == null) {
      throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
    }
    if (!memory.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    memoryMapper.deleteById(memoryId);
  }

  /** 增加引用次数 */
  public void incrementTimesUsed(Long memoryId) {
    UserMemory memory = memoryMapper.selectById(memoryId);
    if (memory != null) {
      memory.setTimesUsed(memory.getTimesUsed() + 1);
      memoryMapper.updateById(memory);
    }
  }

  /** 获取用户在指定分类下的记忆（用于注入到 LLM 上下文） */
  public List<MemoryRespDTO> getContextMemories(Long userId, int limit) {
    LambdaQueryWrapper<UserMemory> wrapper =
        new LambdaQueryWrapper<UserMemory>()
            .eq(UserMemory::getUserId, userId)
            .orderByDesc(UserMemory::getImportance)
            .orderByDesc(UserMemory::getTimesUsed)
            .last("LIMIT " + limit);

    return memoryMapper.selectList(wrapper).stream().map(this::toDTO).toList();
  }

  private MemoryRespDTO toDTO(UserMemory entity) {
    MemoryRespDTO dto = new MemoryRespDTO();
    dto.setId(entity.getId());
    dto.setCategory(entity.getCategory());
    dto.setContent(entity.getContent());
    dto.setSource(entity.getSource());
    dto.setImportance(entity.getImportance());
    dto.setTimesUsed(entity.getTimesUsed());
    dto.setCreateTime(entity.getCreateTime());
    dto.setUpdateTime(entity.getUpdateTime());
    return dto;
  }
}
