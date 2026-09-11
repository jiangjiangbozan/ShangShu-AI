package com.ai.api.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页结果封装（Dify 式 keyset 分页）。
 *
 * <p>借鉴 Dify {@code InfiniteScrollPagination}（{@code data}/{@code limit}/{@code has_more}）：
 * 游标分页通过"上一页最后一条记录的排序键"定位下一页，不依赖 offset，可避免深分页性能问题， 且翻页过程中新增/删除数据不会导致记录重复或遗漏。
 *
 * <p>注意：与 {@link PageResult}（offset 分页 + total）不同，游标分页不返回 total， 由调用方用 {@code limit + 1} 技巧判断
 * {@code hasMore}（见各 Service 实现）。
 *
 * @author add-dify-cursor-pagination
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "游标分页结果")
public class CursorPage<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "当前页数据列表")
  private List<T> data;

  @Schema(description = "每页大小", example = "20")
  private Integer limit;

  @Schema(description = "是否还有更多数据", example = "true")
  private Boolean hasMore;

  /**
   * 构造空结果（游标无效或数据为空时使用）。
   *
   * @param limit 每页大小
   * @param <T> 数据类型
   * @return CursorPage
   */
  public static <T> CursorPage<T> empty(int limit) {
    return new CursorPage<>(Collections.emptyList(), limit, false);
  }

  /**
   * 构造游标分页结果。
   *
   * @param data 当前页数据列表
   * @param limit 每页大小
   * @param hasMore 是否还有更多数据
   * @param <T> 数据类型
   * @return CursorPage
   */
  public static <T> CursorPage<T> of(List<T> data, int limit, boolean hasMore) {
    return new CursorPage<>(data == null ? Collections.emptyList() : data, limit, hasMore);
  }
}
