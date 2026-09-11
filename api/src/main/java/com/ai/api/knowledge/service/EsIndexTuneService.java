package com.ai.api.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ES 索引性能与容量优化 Service（add-p3-rag-feedback-ops）。
 *
 * <p>批量导入文档前临时关闭 {@code refresh_interval}（设为 -1），导入完成后恢复默认值（1s）， 大幅提升大批量写入吞吐。
 *
 * <p>分片容量规划：按 30GB/分片标准规划索引分片数，结合业务数据量动态调整副本策略。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class EsIndexTuneService {

  private static final String DEFAULT_REFRESH_INTERVAL = "1s";

  private final ElasticsearchClient esClient;

  @Value("${es.index.tune.shard-size-gb:30}")
  private int shardSizeGb;

  public EsIndexTuneService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  /**
   * 暂停索引刷新（批量导入前调用）。
   *
   * <p>设 {@code refresh_interval = -1}，期间新写入数据不生成新 lucene segment，查询仍能看到旧 segment。
   *
   * @param indexName 索引名
   */
  public void pauseRefresh(String indexName) {
    updateRefreshInterval(indexName, "-1");
    log.info("[EsIndexTuneService][暂停索引刷新: index={}]", indexName);
  }

  /**
   * 恢复索引刷新（批量导入完成后调用）。
   *
   * <p>恢复 {@code refresh_interval = 1s}，查询可看到新写入的数据。
   */
  public void resumeRefresh(String indexName) {
    updateRefreshInterval(indexName, DEFAULT_REFRESH_INTERVAL);
    log.info("[EsIndexTuneService][恢复索引刷新: index={}]", indexName);
  }

  /**
   * 按数据量计算推荐分片数（30GB/分片标准）。
   *
   * @param dataGb 预计数据量（GB）
   * @return 推荐主分片数（最小 1，向上取整）
   */
  public int recommendShardCount(double dataGb) {
    if (dataGb <= 0) {
      return 1;
    }
    return Math.max(1, (int) Math.ceil(dataGb / shardSizeGb));
  }

  private void updateRefreshInterval(String indexName, String value) {
    try {
      PutIndicesSettingsRequest request =
          PutIndicesSettingsRequest.of(
              b -> b.index(indexName).settings(s -> s.refreshInterval(ri -> ri.time(value))));
      esClient.indices().putSettings(request);
    } catch (IOException e) {
      log.warn(
          "[EsIndexTuneService][更新 refresh_interval 失败（不影响主流程）: index={}, value={}, error={}]",
          indexName,
          value,
          e.getMessage());
    } catch (Exception e) {
      log.error(
          "[EsIndexTuneService][更新 refresh_interval 异常: index={}, value={}, error={}]",
          indexName,
          value,
          e.getMessage());
    }
  }
}
