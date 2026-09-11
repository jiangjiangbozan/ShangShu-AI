package com.ai.api.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页结果封装
 *
 * <p>依据《企业Java后端开发规范.md》§3.2：分页接口统一返回 {@code Result<PageResult<T>>}。
 *
 * @author bootstrap-project
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "当前页数据列表")
  private List<T> list;

  @Schema(description = "总记录数", example = "100")
  private Long total;

  /**
   * 构造空结果。
   *
   * @param <T> 数据类型
   * @return PageResult
   */
  public static <T> PageResult<T> empty() {
    return new PageResult<>(Collections.emptyList(), 0L);
  }

  /**
   * 构造分页结果。
   *
   * @param list 数据列表
   * @param total 总数
   * @param <T> 数据类型
   * @return PageResult
   */
  public static <T> PageResult<T> of(List<T> list, Long total) {
    return new PageResult<>(
        list == null ? Collections.emptyList() : list, total == null ? 0L : total);
  }
}
