package com.ai.api.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.api.agent.config.AgentProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ShortTermMemory} 单元测试（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@DisplayName("短期记忆测试")
class ShortTermMemoryTest {

  private ShortTermMemory memory;
  private AgentProperties properties;

  @BeforeEach
  void setUp() {
    properties = new AgentProperties();
    properties.getMemory().setWindowSize(3);
    properties.getMemory().setTokenThreshold(100);
    memory = new ShortTermMemory(properties);
  }

  @Test
  @DisplayName("追加单条消息应可检索")
  void appendSingle_shouldRetrieve() {
    memory.append(1L, "user", "hello", null);
    List<Map<String, Object>> result = memory.retrieve(1L);
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("role", "user").containsEntry("content", "hello");
  }

  @Test
  @DisplayName("不同 runtime 隔离")
  void differentRuntime_shouldIsolate() {
    memory.append(1L, "user", "msg1", null);
    memory.append(2L, "user", "msg2", null);
    assertThat(memory.retrieve(1L)).hasSize(1);
    assertThat(memory.retrieve(2L)).hasSize(1);
    assertThat(memory.retrieve(1L).get(0)).containsEntry("content", "msg1");
  }

  @Test
  @DisplayName("超过窗口大小应丢弃最早非 system 消息")
  void exceedWindow_shouldDropEarliest() {
    memory.append(1L, "user", "msg1", null);
    memory.append(1L, "assistant", "msg2", null);
    memory.append(1L, "user", "msg3", null);
    memory.append(1L, "assistant", "msg4", null);
    memory.append(1L, "user", "msg5", null);

    List<Map<String, Object>> result = memory.retrieve(1L);
    // 窗口=3，只保留最后 3 条
    assertThat(result).hasSize(3);
    assertThat(result.get(0)).containsEntry("content", "msg3");
    assertThat(result.get(2)).containsEntry("content", "msg5");
  }

  @Test
  @DisplayName("system 消息不参与窗口计数")
  void systemMessages_shouldNotCountInWindow() {
    memory.append(1L, "system", "sys-instruction", null);
    memory.append(1L, "user", "msg1", null);
    memory.append(1L, "user", "msg2", null);
    memory.append(1L, "user", "msg3", null);
    memory.append(1L, "user", "msg4", null);

    List<Map<String, Object>> result = memory.retrieve(1L);
    // system 不计数，应保留 sys + 最近 3 条 user
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).containsEntry("role", "system");
    assertThat(result.get(1)).containsEntry("content", "msg2");
  }

  @Test
  @DisplayName("超过 token 阈值应触发压缩")
  void exceedTokenThreshold_shouldCompress() {
    properties.getMemory().setWindowSize(20); // 放大窗口，确保先触发 token 阈值
    properties.getMemory().setTokenThreshold(10); // 阈值 10 token
    memory = new ShortTermMemory(properties);

    // 每条 10 字符 ≈ 3 token，4 条 ≈ 12 token 超阈值
    memory.append(1L, "user", "0123456789", null);
    memory.append(1L, "user", "0123456789", null);
    memory.append(1L, "user", "0123456789", null);
    memory.append(1L, "user", "0123456789", null);

    List<Map<String, Object>> result = memory.retrieve(1L);
    // 应包含 summary 消息
    boolean hasSummary =
        result.stream()
            .anyMatch(
                m -> {
                  Object c = m.get("content");
                  return c instanceof String s && s.startsWith("[summary]");
                });
    assertThat(hasSummary).isTrue();
  }

  @Test
  @DisplayName("tokenUsage 应返回非零")
  void tokenUsage_shouldReturnPositive() {
    memory.append(1L, "user", "hello world", null);
    int usage = memory.tokenUsage(1L);
    assertThat(usage).isPositive();
  }

  @Test
  @DisplayName("clear 后记忆为空")
  void clear_shouldEmptyMemory() {
    memory.append(1L, "user", "hello", null);
    memory.clear(1L);
    assertThat(memory.retrieve(1L)).isEmpty();
    assertThat(memory.tokenUsage(1L)).isZero();
  }

  @Test
  @DisplayName("metadata 应保留")
  void metadata_shouldBePreserved() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("skill_name", "calculator");
    meta.put("step_index", 2);
    memory.append(1L, "tool", "result", meta);

    List<Map<String, Object>> result = memory.retrieve(1L);
    assertThat(result.get(0)).containsKey("metadata");
    @SuppressWarnings("unchecked")
    Map<String, Object> m = (Map<String, Object>) result.get(0).get("metadata");
    assertThat(m).containsEntry("skill_name", "calculator");
  }

  @Test
  @DisplayName("空 runtime 检索应返回空列表")
  void emptyRuntime_shouldReturnEmpty() {
    assertThat(memory.retrieve(999L)).isEmpty();
    assertThat(memory.tokenUsage(999L)).isZero();
  }
}
