package com.ai.api.dataanalysis.service;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

/**
 * 生成 SQL 的安全校验器（正则快速过滤 + AST 白名单校验，双层防御）。
 *
 * <p>NL2SQL 生成的 SQL 直接面向数据库执行，攻击面在于 LLM 可能被提示注入诱导生成恶意 SQL， 因此校验必须做"白名单式"判断，不能只依赖黑名单正则：
 *
 * <ul>
 *   <li>第一层（正则快速过滤，黑名单）：去注释 → 单语句约束 → SELECT 开头 → 危险关键字 / INTO OUTFILE 快速拒绝。 只承担"廉价第一道"，不是安全底线。
 *   <li>第二层（AST 解析，白名单）：JSqlParser 解析成语法树 → 语句类型必须是 {@code Select}（DROP/DELETE/UPDATE
 *       无论怎么写都解析成对应类型，不可能伪装）→ 拒绝 FOR UPDATE / FOR SHARE / SELECT INTO → 用 {@link TablesNamesFinder}
 *       提取全部真实表名（含 JOIN、子查询）并 ⊆ 白名单 → 归一化回写 SQL（注释/大小写/编码变形全部失效）→ 补 LIMIT 兜底。
 * </ul>
 *
 * <p>真正的安全底线是数据库账号权限收敛（应用账号仅授 SELECT），SqlGuard 只是防御纵深的一层。
 *
 * @author add-data-analysis
 */
@Component
public class SqlGuard {

  /** 与 DataSchemaCatalog 一致的白名单表。 */
  private static final Set<String> ALLOW_TABLES =
      Set.of(
          "sys_user",
          "chat_session",
          "chat_message",
          "sys_llm_log",
          "sys_model_config",
          "knowledge_base",
          "knowledge_document",
          "agent_runtime");

  /** 第一层快速过滤：危险关键字黑名单（注释已在前面剥除，可挡住大部分直接拼写）。 */
  private static final Pattern FORBIDDEN_KEYWORDS =
      Pattern.compile(
          "\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|call|exec|"
              + "load_file|sleep|benchmark)\\b",
          Pattern.CASE_INSENSITIVE);

  /** 第一层快速过滤：文件导出类（SELECT ... INTO OUTFILE/DUMPFILE）。 */
  private static final Pattern INTO_OUTFILE_PATTERN =
      Pattern.compile("\\binto\\s+(outfile|dumpfile)\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern LIMIT_PATTERN =
      Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);

  /**
   * 校验并规范化 SQL，返回可安全执行的 SQL。
   *
   * @param rawSql LLM 生成的原始 SQL
   * @return 校验通过的安全 SQL（AST 归一化后的标准形态，已确保带 LIMIT）
   */
  public String sanitize(String rawSql) {
    // ===== 第一层：正则快速过滤（黑名单，快速拒绝明显不合规的 SQL）=====
    if (rawSql == null || rawSql.isBlank()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "生成的 SQL 为空");
    }
    // 先剥注释，防止把关键字藏进注释绕过正则（如 SEL/*comment*/ECT）
    String sql = rawSql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*--.*$", "").trim();
    // 去末尾分号
    while (sql.endsWith(";")) {
      sql = sql.substring(0, sql.length() - 1).trim();
    }
    if (sql.isBlank()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "生成的 SQL 为空");
    }
    // 多语句一票否决（提示注入拼出 SELECT ...; DROP ... 在此拦截）
    if (sql.contains(";")) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "仅允许单条查询语句");
    }
    if (!sql.regionMatches(true, 0, "SELECT", 0, 6)) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "仅允许 SELECT 查询");
    }
    if (FORBIDDEN_KEYWORDS.matcher(sql).find()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "SQL 包含被禁止的关键字");
    }
    if (INTO_OUTFILE_PATTERN.matcher(sql).find()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "禁止文件导出类查询");
    }

    // ===== 第二层：AST 解析（白名单，结构性校验，真正的拦截主力）=====
    Statement stmt;
    try {
      stmt = CCJSqlParserUtil.parse(sql);
    } catch (JSQLParserException e) {
      // 解析失败说明 LLM 输出不是合法 SQL，宁可错杀也不放行
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "SQL 无法解析，已拒绝执行");
    }
    if (!(stmt instanceof Select select)) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "仅允许 SELECT 查询");
    }
    // 禁止锁读：SELECT ... FOR UPDATE / FOR SHARE
    if (select.getForUpdateTable() != null || select.getForMode() != null) {
      throw new BusinessException(
          ErrorCode.DATA_ANALYSIS_SQL_INVALID, "禁止加锁查询（FOR UPDATE/FOR SHARE）");
    }
    // 禁止 SELECT INTO（含 SELECT INTO OUTFILE 等结果导出类）
    if (select.getPlainSelect() != null
        && select.getPlainSelect().getIntoTables() != null
        && !select.getPlainSelect().getIntoTables().isEmpty()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "禁止 SELECT INTO 查询");
    }
    // 表白名单：AST 提取全部真实表名，注释/大小写/编码变形在语法树层面全部失效
    Set<String> tables = collectTables(stmt);
    if (tables.isEmpty()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "查询必须引用至少一个白名单表");
    }
    for (String table : tables) {
      if (!ALLOW_TABLES.contains(table)) {
        throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "仅允许查询白名单数据表");
      }
    }
    // 归一化回写：执行的是 AST 重排后的标准形态 SQL（丢弃一切文本级变形），
    // 再对归一化结果做一次危险函数兜底（黑名单复核，防漏网之鱼）
    String normalized = select.toString();
    if (FORBIDDEN_KEYWORDS.matcher(normalized).find()) {
      throw new BusinessException(ErrorCode.DATA_ANALYSIS_SQL_INVALID, "SQL 包含被禁止的关键字");
    }
    return normalizeLimit(normalized);
  }

  /** 从 AST 提取全部真实表名（含 JOIN、子查询），归一化为小写裸表名。 */
  private Set<String> collectTables(Statement stmt) {
    List<String> raw = new TablesNamesFinder().getTableList(stmt);
    Set<String> tables = new HashSet<>();
    for (String t : raw) {
      if (t == null || t.isBlank()) {
        continue;
      }
      // 去除反引号/双引号/方括号引用符，剥掉 schema 前缀（db.table → table）
      String norm = t.replace("`", "").replace("\"", "").replace("[", "").replace("]", "");
      int dot = norm.lastIndexOf('.');
      if (dot >= 0) {
        norm = norm.substring(dot + 1);
      }
      tables.add(norm.toLowerCase());
    }
    return tables;
  }

  private String normalizeLimit(String sql) {
    if (LIMIT_PATTERN.matcher(sql).find()) {
      return sql;
    }
    return sql + " LIMIT 200";
  }
}
