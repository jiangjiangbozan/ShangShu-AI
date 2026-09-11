package com.ai.api.character.controller;

import com.ai.api.character.dto.CharacterCardCreateReqDTO;
import com.ai.api.character.dto.CharacterCardRespDTO;
import com.ai.api.character.dto.CharacterCardUpdateReqDTO;
import com.ai.api.character.service.CharacterCardService;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.ai.api.common.result.Result;
import com.ai.api.identity.security.AuthUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/character-cards")
@RequiredArgsConstructor
public class CharacterCardController {

  private final CharacterCardService characterCardService;

  /** 创建角色卡 */
  @PostMapping
  public Result<CharacterCardRespDTO> createCharacterCard(
      @Valid @RequestBody CharacterCardCreateReqDTO req) {
    Long userId = getCurrentUserId();
    return Result.success(characterCardService.createCharacterCard(userId, req));
  }

  /** 获取用户角色卡列表 */
  @GetMapping
  public Result<List<CharacterCardRespDTO>> listCharacterCards() {
    Long userId = getCurrentUserId();
    return Result.success(characterCardService.listCharacterCards(userId));
  }

  /** 获取公开角色卡（市场/广场） */
  @GetMapping("/public")
  public Result<List<CharacterCardRespDTO>> listPublicCharacterCards(
      @RequestParam(required = false) String keyword) {
    return Result.success(characterCardService.listPublicCharacterCards(keyword));
  }

  /** 获取角色卡详情 */
  @GetMapping("/{id}")
  public Result<CharacterCardRespDTO> getCharacterCard(@PathVariable Long id) {
    return Result.success(characterCardService.getCharacterCard(id));
  }

  /** 更新角色卡 */
  @PutMapping("/{id}")
  public Result<CharacterCardRespDTO> updateCharacterCard(
      @PathVariable Long id, @Valid @RequestBody CharacterCardUpdateReqDTO req) {
    Long userId = getCurrentUserId();
    return Result.success(characterCardService.updateCharacterCard(userId, id, req));
  }

  /** 删除角色卡 */
  @DeleteMapping("/{id}")
  public Result<Void> deleteCharacterCard(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    characterCardService.deleteCharacterCard(userId, id);
    return Result.success(null);
  }

  /** 设为默认角色卡 */
  @PutMapping("/{id}/default")
  public Result<CharacterCardRespDTO> setDefault(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    return Result.success(characterCardService.setDefault(userId, id));
  }

  /** 获取默认角色卡 */
  @GetMapping("/default")
  public Result<CharacterCardRespDTO> getDefault() {
    Long userId = getCurrentUserId();
    return Result.success(characterCardService.getDefaultCharacterCard(userId));
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails details)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }
    return details.userId();
  }
}
