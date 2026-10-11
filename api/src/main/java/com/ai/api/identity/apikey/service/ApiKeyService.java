package com.ai.api.identity.apikey.service;

import com.ai.api.identity.apikey.entity.ApiKey;
import com.ai.api.identity.apikey.mapper.ApiKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** API 密钥管理服务 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

  private final ApiKeyMapper apiKeyMapper;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** 创建 API 密钥，返回含明文密钥的对象（仅此一次可见） */
  public ApiKey create(String name, Long creatorId) {
    // 生成密钥：sk-ai-{32位随机hex}
    byte[] randomBytes = new byte[16];
    SECURE_RANDOM.nextBytes(randomBytes);
    String randomHex = HexFormat.of().formatHex(randomBytes);
    String plainKey = "sk-ai-" + randomHex;
    String prefix = plainKey.substring(0, 10); // sk-ai-xxxx
    String hash = sha256(plainKey);

    ApiKey entity = new ApiKey();
    entity.setName(name);
    entity.setKeyPrefix(prefix);
    entity.setKeyHash(hash);
    entity.setCreatorId(creatorId);
    entity.setEnabled(1);
    apiKeyMapper.insert(entity);

    // 仅创建时返回明文
    entity.setKeyPlain(plainKey);
    return entity;
  }

  /** 分页查询 */
  public Page<ApiKey> list(int page, int size) {
    return apiKeyMapper.selectPage(
        new Page<>(page, size), new QueryWrapper<ApiKey>().orderByDesc("create_time"));
  }

  /** 启用/禁用 */
  public void toggleEnabled(Long id, boolean enabled) {
    ApiKey entity = apiKeyMapper.selectById(id);
    if (entity == null) throw new IllegalArgumentException("API 密钥不存在");
    entity.setEnabled(enabled ? 1 : 0);
    apiKeyMapper.updateById(entity);
  }

  /** 删除 */
  public void delete(Long id) {
    apiKeyMapper.deleteById(id);
  }

  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (Exception e) {
      throw new RuntimeException("SHA-256 哈希失败", e);
    }
  }
}
