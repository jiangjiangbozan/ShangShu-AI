package com.ai.api.ops.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ES 快照仓库自动注册器（add-p3-rag-feedback-ops）。
 *
 * <p>应用启动时若 {@code backup.es.repository} 不存在则自动注册为 fs 类型 repository， 免去部署时手工执行 {@code PUT
 * /_snapshot/xxx}。仓库路径需在 ES {@code path.repo} 白名单内（docker-compose.yml 已配置 {@code
 * /usr/share/elasticsearch/backup}）。
 *
 * <p>注册失败仅记录 WARN，不阻断启动（快照功能降级）。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Configuration
public class EsSnapshotRepositoryInitializer {

  private final ElasticsearchClient esClient;

  @Value("${backup.es.repository:kb_backup_repo}")
  private String repository;

  @Value("${backup.es.location:/usr/share/elasticsearch/backup}")
  private String location;

  public EsSnapshotRepositoryInitializer(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @PostConstruct
  public void registerRepository() {
    try {
      // PUT 幂等：仓库已存在且配置一致时直接返回 acknowledged
      esClient
          .snapshot()
          .createRepository(
              r ->
                  r.name(repository)
                      .repository(
                          rep ->
                              rep.fs(fs -> fs.settings(s -> s.location(location).compress(true)))));
      log.info(
          "[EsSnapshotRepositoryInitializer][快照仓库就绪: repo={}, location={}]", repository, location);
    } catch (Exception e) {
      log.warn(
          "[EsSnapshotRepositoryInitializer][快照仓库注册失败（不阻断启动）: repo={}, error={}]",
          repository,
          e.getMessage());
    }
  }
}
