package com.ai.api.ops.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ES 知识库索引快照备份服务（add-p3-rag-feedback-ops）。
 *
 * <p>每周日 02:30 对知识库向量索引做快照；保留近 4 个快照。
 *
 * <p>需提前在 ES 集群注册 snapshot repository（fs 类型）：
 *
 * <pre>{@code
 * PUT /_snapshot/kb_backup_repo
 * {
 *   "type": "fs",
 *   "settings": { "location": "/data/es-backup" }
 * }
 * }</pre>
 *
 * <p>本服务仅做创建/清理/恢复 API 调用，不负责 repository 注册（部署时一次性操作）。
 *
 * @author add-p3-rag-feedback-ops
 */
@Slf4j
@Service
public class EsSnapshotService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String SNAPSHOT_PREFIX = "snapshot_";

  private final ElasticsearchClient esClient;

  @Value("${backup.es.repository:kb_backup_repo}")
  private String repository;

  @Value("${backup.es.index-pattern:kb_*}")
  private String indexPattern;

  @Value("${backup.es.retention-snapshots:4}")
  private int retentionSnapshots;

  public EsSnapshotService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  /** 每周日 02:30 触发快照。 */
  @Scheduled(cron = "${backup.es.cron:0 30 2 * * SUN}")
  public void snapshotWeekly() {
    String name = SNAPSHOT_PREFIX + LocalDate.now().format(DATE_FMT);
    try {
      CreateSnapshotRequest request =
          CreateSnapshotRequest.of(
              b ->
                  b.repository(repository)
                      .snapshot(name)
                      .indices(indexPattern)
                      .waitForCompletion(false));
      esClient.snapshot().create(request);
      log.info("[EsSnapshotService][快照创建请求已提交: repo={}, snapshot={}]", repository, name);
      purgeOldSnapshots();
    } catch (IOException e) {
      log.error(
          "[EsSnapshotService][快照创建失败（不影响主流程）: repo={}, snapshot={}, error={}]",
          repository,
          name,
          e.getMessage());
    }
  }

  /**
   * 恢复指定日期快照（误删数据后人工触发）。
   *
   * @param dateStr yyyyMMdd 格式日期
   */
  public void restore(String dateStr) throws IOException {
    String name = SNAPSHOT_PREFIX + dateStr;
    esClient
        .snapshot()
        .restore(b -> b.repository(repository).snapshot(name).waitForCompletion(true));
    log.info("[EsSnapshotService][快照恢复完成: repo={}, snapshot={}]", repository, name);
  }

  /** 清理超出保留数量的旧快照。 */
  private void purgeOldSnapshots() {
    try {
      java.util.stream.Stream<String> snapshotStream =
          esClient.snapshot().get(g -> g.repository(repository)).snapshots().stream()
              .map(
                  new java.util.function.Function<
                      co.elastic.clients.elasticsearch.snapshot.SnapshotInfo, String>() {
                    @Override
                    public String apply(co.elastic.clients.elasticsearch.snapshot.SnapshotInfo s) {
                      return s.snapshot();
                    }
                  });
      List<String> snapshots =
          snapshotStream.filter(n -> n != null && n.startsWith(SNAPSHOT_PREFIX)).sorted().toList();
      int toDelete = Math.max(0, snapshots.size() - retentionSnapshots);
      for (int i = 0; i < toDelete; i++) {
        String name = snapshots.get(i);
        esClient
            .snapshot()
            .delete(DeleteSnapshotRequest.of(d -> d.repository(repository).snapshot(name)));
        log.info("[EsSnapshotService][清理旧快照: snapshot={}]", name);
      }
    } catch (Exception e) {
      log.warn("[EsSnapshotService][清理旧快照失败（不影响主流程）: error={}]", e.getMessage());
    }
  }
}
