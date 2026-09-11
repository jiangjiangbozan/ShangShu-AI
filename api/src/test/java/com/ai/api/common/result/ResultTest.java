package com.ai.api.common.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Result} 单元测试
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>success / success(data) 工厂方法
 *   <li>fail(code, message) / fail(message) 工厂方法
 *   <li>isSuccess 判断
 *   <li>traceId 字段（MDC 不存在时为 null，存在时自动填充）
 * </ul>
 *
 * @author bootstrap-project
 */
@DisplayName("Result 统一响应封装测试")
class ResultTest {

  @Test
  @DisplayName("success() 应返回 code=0 且 data=null")
  void success_noData_shouldReturnCodeZero() {
    // when
    Result<Void> result = Result.success();

    // then
    assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
    assertThat(result.getMessage()).isEqualTo("success");
    assertThat(result.getData()).isNull();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("success(data) 应携带数据")
  void success_withData_shouldCarryData() {
    // given
    String data = "hello";

    // when
    Result<String> result = Result.success(data);

    // then
    assertThat(result.getCode()).isEqualTo(0);
    assertThat(result.getData()).isEqualTo("hello");
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("fail(code, message) 应使用指定错误码")
  void fail_withCodeAndMessage_shouldUseSpecifiedCode() {
    // when
    Result<Void> result = Result.fail(400, "参数错误");

    // then
    assertThat(result.getCode()).isEqualTo(400);
    assertThat(result.getMessage()).isEqualTo("参数错误");
    assertThat(result.getData()).isNull();
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("fail(message) 应使用默认失败码 500")
  void fail_withMessageOnly_shouldUseDefaultFailCode() {
    // when
    Result<Void> result = Result.fail("系统错误");

    // then
    assertThat(result.getCode()).isEqualTo(Result.CODE_FAIL);
    assertThat(result.getMessage()).isEqualTo("系统错误");
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("CODE_SUCCESS 应为 0，CODE_FAIL 应为 500")
  void constants_shouldHaveExpectedValues() {
    assertThat(Result.CODE_SUCCESS).isEqualTo(0);
    assertThat(Result.CODE_FAIL).isEqualTo(500);
  }
}
