package com.ai.api.agent.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link Dag} 单元测试（add-agent-runtime）。
 *
 * @author add-agent-runtime
 */
@DisplayName("DAG 拓扑校验与排序测试")
class DagTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Dag parse(String json) {
    return Dag.parse(objectMapper, json);
  }

  @Test
  @DisplayName("单节点 DAG 校验通过")
  void singleNode_shouldValidate() {
    Dag dag = parse("{\"nodes\":[{\"id\":\"n1\",\"skill\":\"calculator\"}],\"edges\":[]}");
    dag.validate();
    assertThat(dag.getNodes()).hasSize(1);
  }

  @Test
  @DisplayName("线性 DAG n1 → n2 → n3 拓扑排序正确")
  void linearDag_shouldTopologicalSort() {
    Dag dag =
        parse(
            "{\"nodes\":["
                + "{\"id\":\"n1\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n2\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n3\",\"skill\":\"calculator\"}"
                + "],\"edges\":["
                + "{\"from\":\"n1\",\"to\":\"n2\"},"
                + "{\"from\":\"n2\",\"to\":\"n3\"}"
                + "]}");
    dag.validate();
    List<Dag.Node> sorted = dag.topologicalSort();
    assertThat(sorted).extracting(Dag.Node::getId).containsExactly("n1", "n2", "n3");
  }

  @Test
  @DisplayName("含环的 DAG 校验失败")
  void cycleDag_shouldFailValidation() {
    Dag dag =
        parse(
            "{\"nodes\":["
                + "{\"id\":\"n1\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n2\",\"skill\":\"calculator\"}"
                + "],\"edges\":["
                + "{\"from\":\"n1\",\"to\":\"n2\"},"
                + "{\"from\":\"n2\",\"to\":\"n1\"}"
                + "]}");
    assertThatThrownBy(dag::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycle");
  }

  @Test
  @DisplayName("空节点 DAG 校验失败")
  void emptyDag_shouldFail() {
    Dag dag = parse("{\"nodes\":[],\"edges\":[]}");
    assertThatThrownBy(dag::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no nodes");
  }

  @Test
  @DisplayName("边引用不存在节点应校验失败")
  void edgeToUnknownNode_shouldFail() {
    Dag dag =
        parse(
            "{\"nodes\":[{\"id\":\"n1\",\"skill\":\"calculator\"}],"
                + "\"edges\":[{\"from\":\"n1\",\"to\":\"n2\"}]}");
    assertThatThrownBy(dag::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown node");
  }

  @Test
  @DisplayName("菱形依赖 n1 → n2,n3 → n4 校验通过")
  void diamondDag_shouldValidate() {
    Dag dag =
        parse(
            "{\"nodes\":["
                + "{\"id\":\"n1\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n2\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n3\",\"skill\":\"calculator\"},"
                + "{\"id\":\"n4\",\"skill\":\"calculator\"}"
                + "],\"edges\":["
                + "{\"from\":\"n1\",\"to\":\"n2\"},"
                + "{\"from\":\"n1\",\"to\":\"n3\"},"
                + "{\"from\":\"n2\",\"to\":\"n4\"},"
                + "{\"from\":\"n3\",\"to\":\"n4\"}"
                + "]}");
    dag.validate();
    assertThat(dag.getNodes()).hasSize(4);
  }

  @Test
  @DisplayName("非法 JSON 应抛 IllegalArgumentException")
  void invalidJson_shouldThrow() {
    assertThatThrownBy(() -> parse("not a json")).isInstanceOf(IllegalArgumentException.class);
  }
}
