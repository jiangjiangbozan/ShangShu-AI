package com.ai.api.common.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PageResult} 单元测试
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>empty() 应返回空列表与 0 总数
 *   <li>of(list, total) 应正确封装
 *   <li>of(null, null) 应降级为空列表与 0 总数
 * </ul>
 *
 * @author bootstrap-project
 */
@DisplayName("PageResult 分页封装测试")
class PageResultTest {

  @Test
  @DisplayName("empty() 应返回空列表与 0 总数")
  void empty_shouldReturnEmptyListAndZeroTotal() {
    // when
    PageResult<String> result = PageResult.empty();

    // then
    assertThat(result.getList()).isEmpty();
    assertThat(result.getTotal()).isZero();
  }

  @Test
  @DisplayName("of(list, total) 应正确封装")
  void of_shouldWrapListAndTotal() {
    // given
    List<String> list = Arrays.asList("a", "b", "c");

    // when
    PageResult<String> result = PageResult.of(list, 100L);

    // then
    assertThat(result.getList()).containsExactly("a", "b", "c");
    assertThat(result.getTotal()).isEqualTo(100L);
  }

  @Test
  @DisplayName("of(null, null) 应降级为空列表与 0 总数")
  void of_withNulls_shouldFallbackToEmptyDefaults() {
    // when
    PageResult<String> result = PageResult.of(null, null);

    // then
    assertThat(result.getList()).isNotNull().isEmpty();
    assertThat(result.getTotal()).isZero();
  }

  @Test
  @DisplayName("of(空列表, 0) 应保持空列表与 0 总数")
  void of_withEmptyList_shouldKeepEmpty() {
    // when
    PageResult<String> result = PageResult.of(Collections.emptyList(), 0L);

    // then
    assertThat(result.getList()).isEmpty();
    assertThat(result.getTotal()).isZero();
  }
}
