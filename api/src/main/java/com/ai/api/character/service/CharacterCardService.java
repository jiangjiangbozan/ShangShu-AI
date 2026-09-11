package com.ai.api.character.service;

import com.ai.api.character.dto.CharacterCardCreateReqDTO;
import com.ai.api.character.dto.CharacterCardRespDTO;
import com.ai.api.character.dto.CharacterCardUpdateReqDTO;
import com.ai.api.character.entity.CharacterCard;
import com.ai.api.character.mapper.CharacterCardMapper;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterCardService {

  private final CharacterCardMapper characterCardMapper;

  /** 创建角色卡 */
  public CharacterCardRespDTO createCharacterCard(Long userId, CharacterCardCreateReqDTO req) {
    CharacterCard card = new CharacterCard();
    BeanUtils.copyProperties(req, card);
    card.setUserId(userId);
    card.setIsPublic(req.getIsPublic() != null ? req.getIsPublic() : false);
    card.setIsDefault(false);
    card.setUseCount(0);
    card.setLikeCount(0);
    characterCardMapper.insert(card);
    return toResp(card);
  }

  /** 获取用户可用的角色卡列表（自己创建的 + 公开的 + 系统预设的） */
  public List<CharacterCardRespDTO> listCharacterCards(Long userId) {
    LambdaQueryWrapper<CharacterCard> wrapper = new LambdaQueryWrapper<>();
    wrapper.and(
        w ->
            w.eq(CharacterCard::getUserId, userId)
                .or()
                .eq(CharacterCard::getIsPublic, true)
                .or()
                .eq(CharacterCard::getUserId, 0L));
    wrapper
        .orderByDesc(CharacterCard::getIsDefault)
        .orderByDesc(CharacterCard::getUseCount)
        .orderByDesc(CharacterCard::getCreateTime);
    return characterCardMapper.selectList(wrapper).stream()
        .map(this::toResp)
        .collect(Collectors.toList());
  }

  /** 获取公开角色卡（市场/广场） */
  public List<CharacterCardRespDTO> listPublicCharacterCards(String keyword) {
    LambdaQueryWrapper<CharacterCard> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CharacterCard::getIsPublic, true);
    if (keyword != null && !keyword.isBlank()) {
      String kw = "%" + keyword.trim() + "%";
      wrapper.and(
          w ->
              w.like(CharacterCard::getName, kw)
                  .or()
                  .like(CharacterCard::getDescription, kw)
                  .or()
                  .like(CharacterCard::getTags, kw));
    }
    wrapper.orderByDesc(CharacterCard::getLikeCount).orderByDesc(CharacterCard::getUseCount);
    return characterCardMapper.selectList(wrapper).stream()
        .map(this::toResp)
        .collect(Collectors.toList());
  }

  /** 获取角色卡详情 */
  public CharacterCardRespDTO getCharacterCard(Long id) {
    CharacterCard card = characterCardMapper.selectById(id);
    if (card == null) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    return toResp(card);
  }

  /** 更新角色卡（仅创建者可更新） */
  public CharacterCardRespDTO updateCharacterCard(
      Long userId, Long id, CharacterCardUpdateReqDTO req) {
    CharacterCard card = characterCardMapper.selectById(id);
    if (card == null) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    if (!card.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    if (req.getName() != null) card.setName(req.getName());
    if (req.getAvatar() != null) card.setAvatar(req.getAvatar());
    if (req.getDescription() != null) card.setDescription(req.getDescription());
    if (req.getSystemPrompt() != null) card.setSystemPrompt(req.getSystemPrompt());
    if (req.getOpeningStatement() != null) card.setOpeningStatement(req.getOpeningStatement());
    if (req.getTags() != null) card.setTags(req.getTags());
    if (req.getIsPublic() != null) card.setIsPublic(req.getIsPublic());
    characterCardMapper.updateById(card);
    return toResp(card);
  }

  /** 删除角色卡（仅创建者可删除） */
  @Transactional
  public void deleteCharacterCard(Long userId, Long id) {
    CharacterCard card = characterCardMapper.selectById(id);
    if (card == null) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    if (!card.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    characterCardMapper.deleteById(id);
  }

  /** 设为默认角色卡（取消其他默认） */
  @Transactional
  public CharacterCardRespDTO setDefault(Long userId, Long id) {
    CharacterCard card = characterCardMapper.selectById(id);
    if (card == null) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    if (!card.getUserId().equals(userId) && card.getUserId() != 0L) {
      throw new BusinessException(ErrorCode.CHARACTER_CARD_NOT_FOUND);
    }
    // 取消该用户所有默认
    LambdaQueryWrapper<CharacterCard> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CharacterCard::getUserId, userId);
    List<CharacterCard> userCards = characterCardMapper.selectList(wrapper);
    for (CharacterCard c : userCards) {
      if (Boolean.TRUE.equals(c.getIsDefault())) {
        c.setIsDefault(false);
        characterCardMapper.updateById(c);
      }
    }
    card.setIsDefault(true);
    characterCardMapper.updateById(card);
    return toResp(card);
  }

  /** 增加使用次数 */
  public void incrementUseCount(Long id) {
    CharacterCard card = characterCardMapper.selectById(id);
    if (card != null) {
      card.setUseCount(card.getUseCount() + 1);
      characterCardMapper.updateById(card);
    }
  }

  /** 获取用户的默认角色卡 */
  public CharacterCardRespDTO getDefaultCharacterCard(Long userId) {
    LambdaQueryWrapper<CharacterCard> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CharacterCard::getUserId, userId).eq(CharacterCard::getIsDefault, true);
    wrapper.last("LIMIT 1");
    CharacterCard card = characterCardMapper.selectOne(wrapper);
    if (card == null) {
      // 返回系统默认角色卡
      wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(CharacterCard::getUserId, 0L).eq(CharacterCard::getIsDefault, true);
      wrapper.last("LIMIT 1");
      card = characterCardMapper.selectOne(wrapper);
    }
    return card != null ? toResp(card) : null;
  }

  private CharacterCardRespDTO toResp(CharacterCard card) {
    CharacterCardRespDTO resp = new CharacterCardRespDTO();
    BeanUtils.copyProperties(card, resp);
    return resp;
  }
}
