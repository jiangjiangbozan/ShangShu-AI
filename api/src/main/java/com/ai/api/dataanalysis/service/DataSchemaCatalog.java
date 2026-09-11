package com.ai.api.dataanalysis.service;

import com.ai.api.dataanalysis.dto.DataSchemaResponseDTO.ColumnMeta;
import com.ai.api.dataanalysis.dto.DataSchemaResponseDTO.TableMeta;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * NL2SQL 元数据目录。
 *
 * <p>向 LLM 暴露的白名单表/列（已剔除敏感列：密码、盐、API Key、消息正文、Trace 详情等）， 既保证可问数的数据范围，也降低 SQL 注入风险。
 *
 * @author add-data-analysis
 */
@Component
public class DataSchemaCatalog {

  private final List<TableMeta> tables =
      List.of(
          table(
              "sys_user",
              "系统用户表",
              col("id", "用户 ID", "int"),
              col("username", "用户名", "string"),
              col("email", "邮箱", "string"),
              col("status", "状态 1=正常 0=禁用", "int"),
              col("last_login_at", "最近登录时间", "datetime"),
              col("create_time", "注册时间", "datetime")),
          table(
              "chat_session",
              "对话会话表",
              col("id", "会话 ID", "int"),
              col("user_id", "所属用户 ID", "int"),
              col("title", "会话标题", "string"),
              col("model_id", "使用的模型 ID", "string"),
              col("message_count", "消息总数", "int"),
              col("total_tokens", "累计 Token 数", "int"),
              col("create_time", "创建时间", "datetime"),
              col("update_time", "最后更新时间", "datetime")),
          table(
              "chat_message",
              "对话消息表",
              col("id", "消息 ID", "int"),
              col("session_id", "所属会话 ID", "int"),
              col("role", "角色 user/assistant/system", "string"),
              col("prompt_tokens", "输入 Token 数", "int"),
              col("completion_tokens", "输出 Token 数", "int"),
              col("total_tokens", "总 Token 数", "int"),
              col("latency_ms", "响应延迟(毫秒)", "int"),
              col("model_id", "模型 ID", "string"),
              col("create_time", "消息时间", "datetime")),
          table(
              "sys_llm_log",
              "LLM 调用日志表",
              col("id", "日志 ID", "int"),
              col("model_id", "模型 ID", "string"),
              col("prompt_tokens", "输入 Token 数", "int"),
              col("completion_tokens", "输出 Token 数", "int"),
              col("total_tokens", "总 Token 数", "int"),
              col("latency_ms", "调用耗时(毫秒)", "int"),
              col("status", "状态 success/failed", "string"),
              col("create_time", "调用时间", "datetime")),
          table(
              "sys_model_config",
              "模型配置表",
              col("id", "配置 ID", "int"),
              col("provider", "供应商 deepseek/qwen/ollama", "string"),
              col("model_id", "模型标识", "string"),
              col("display_name", "展示名称", "string"),
              col("enabled", "是否启用 1=是 0=否", "int"),
              col("create_time", "创建时间", "datetime")),
          table(
              "knowledge_base",
              "知识库表",
              col("id", "知识库 ID", "int"),
              col("name", "知识库名称", "string"),
              col("owner_id", "所有者用户 ID", "int"),
              col("document_count", "文档总数", "int"),
              col("chunk_count", "分片总数", "int"),
              col("status", "状态 active/disabled", "string"),
              col("create_time", "创建时间", "datetime")),
          table(
              "knowledge_document",
              "知识库文档表",
              col("id", "文档 ID", "int"),
              col("knowledge_base_id", "所属知识库 ID", "int"),
              col("file_name", "文件名", "string"),
              col("file_type", "文件类型 pdf/docx/md/txt/html", "string"),
              col("file_size", "文件大小(字节)", "int"),
              col("chunk_count", "分片数", "int"),
              col("status", "状态 pending/parsing/vectorizing/completed/failed", "string"),
              col("create_time", "上传时间", "datetime")),
          table(
              "agent_runtime",
              "Agent 运行时表",
              col("id", "运行时 ID", "int"),
              col("session_id", "关联会话 ID", "int"),
              col("status", "状态 pending/running/paused/done/failed/cancelled", "string"),
              col("model_id", "模型 ID", "string"),
              col("user_id", "用户 ID", "int"),
              col("create_time", "创建时间", "datetime"),
              col("update_time", "更新时间", "datetime")));

  /** 生成给 LLM 的紧凑 schema 提示文本。 */
  public String buildSchemaPrompt() {
    StringBuilder sb = new StringBuilder("可用数据表（仅限以下表，禁止访问其他表）：\n");
    for (TableMeta t : tables) {
      sb.append("- ").append(t.getName()).append("（").append(t.getComment()).append("）：");
      StringBuilder cols = new StringBuilder();
      for (ColumnMeta c : t.getColumns()) {
        if (cols.length() > 0) {
          cols.append(", ");
        }
        cols.append(c.getName())
            .append("(")
            .append(c.getComment())
            .append(", ")
            .append(c.getType())
            .append(")");
      }
      sb.append(cols).append("\n");
    }
    return sb.toString();
  }

  /** 返回表清单（供客户端展示与 LLM 元数据同步）。 */
  public List<TableMeta> listTables() {
    return tables;
  }

  private static TableMeta table(String name, String comment, ColumnMeta... columns) {
    TableMeta t = new TableMeta();
    t.setName(name);
    t.setComment(comment);
    t.setColumns(List.of(columns));
    return t;
  }

  private static ColumnMeta col(String name, String comment, String type) {
    ColumnMeta c = new ColumnMeta();
    c.setName(name);
    c.setComment(comment);
    c.setType(type);
    return c;
  }
}
