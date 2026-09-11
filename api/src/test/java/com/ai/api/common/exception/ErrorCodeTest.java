package com.ai.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ErrorCode} 单元测试
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>枚举常量的 code 与 message 不为空
 *   <li>asInt() 应返回正确的整数值
 *   <li>BusinessException(ErrorCode) 应正确携带 code 与 message
 *   <li>BusinessException(ErrorCode, message) 应使用自定义 message
 *   <li>BusinessException(code, message) 应支持自定义 code
 * </ul>
 *
 * @author bootstrap-project
 */
@DisplayName("ErrorCode 与 BusinessException 测试")
class ErrorCodeTest {

  @Test
  @DisplayName("所有 ErrorCode 枚举的 code 与 message 不为空")
  void allErrorCodes_shouldHaveCodeAndMessage() {
    // when & then
    for (ErrorCode code : ErrorCode.values()) {
      assertThat(code.getCode()).as("ErrorCode %s 的 code 不应为空", code.name()).isNotBlank();
      assertThat(code.getMessage()).as("ErrorCode %s 的 message 不应为空", code.name()).isNotBlank();
      assertThat(code.asInt()).as("ErrorCode %s 的 asInt 应为正整数", code.name()).isPositive();
    }
  }

  @Test
  @DisplayName("asInt() 应返回正确的整数值")
  void asInt_shouldReturnIntegerValue() {
    assertThat(ErrorCode.COMMON_BAD_PARAM.asInt()).isEqualTo(100);
    assertThat(ErrorCode.MODEL_PING_FAILED.asInt()).isEqualTo(320);
    assertThat(ErrorCode.SESSION_NOT_FOUND.asInt()).isEqualTo(401);
  }

  @Test
  @DisplayName("BusinessException(ErrorCode) 应携带 ErrorCode 的 code 与 message")
  void businessException_withErrorCode_shouldCarryCodeAndMessage() {
    // when
    BusinessException ex = new BusinessException(ErrorCode.MODEL_PING_BAD_PARAM);

    // then
    assertThat(ex.getCode()).isEqualTo(330);
    assertThat(ex.getMessage()).isEqualTo(ErrorCode.MODEL_PING_BAD_PARAM.getMessage());
  }

  @Test
  @DisplayName("BusinessException(ErrorCode, message) 应使用自定义 message")
  void businessException_withErrorCodeAndCustomMessage_shouldUseCustomMessage() {
    // when
    BusinessException ex =
        new BusinessException(ErrorCode.MODEL_NOT_ENABLED, "模型 deepseek-chat 未启用");

    // then
    assertThat(ex.getCode()).isEqualTo(331);
    assertThat(ex.getMessage()).isEqualTo("模型 deepseek-chat 未启用");
  }

  @Test
  @DisplayName("BusinessException(code, message) 应支持自定义 code")
  void businessException_withCustomCodeAndMessage_shouldUseCustomCode() {
    // when
    BusinessException ex = new BusinessException(999, "自定义错误");

    // then
    assertThat(ex.getCode()).isEqualTo(999);
    assertThat(ex.getMessage()).isEqualTo("自定义错误");
  }

  @Test
  @DisplayName("抛出 BusinessException 应为 RuntimeException 子类")
  void businessException_shouldBeRuntimeException() {
    // when & then
    assertThatThrownBy(
            () -> {
              throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
            })
        .isInstanceOf(RuntimeException.class)
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.COMMON_INTERNAL_ERROR.getMessage());
  }
}
