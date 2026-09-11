package com.ai.api.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.api.data.config.AuditMetaObjectHandler;
import com.ai.api.data.config.MybatisPlusConfig;
import com.ai.api.identity.entity.SystemUser;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link SystemUserMapper} 集成测试
 *
 * <p>依据 tasks.md 2.3：使用 Testcontainers MySQL 验证 CRUD + 自定义查询。覆盖：
 *
 * <ul>
 *   <li>BaseMapper.insert / selectById / updateById / deleteById
 *   <li>自定义查询：selectByUsername / selectByEmail
 *   <li>逻辑删除：deleteById 后 selectById 应返回 null
 *   <li>唯一索引（username+deleted, email+deleted）约束
 * </ul>
 *
 * <p>测试依赖 Docker，仅在 {@code DOCKER_HOST} 环境变量存在时启用。
 *
 * @author add-user-auth
 */
@Testcontainers
@SpringBootTest(
    classes = {
      SystemUserMapper.class,
      MybatisPlusConfig.class,
      AuditMetaObjectHandler.class,
      DataSourceAutoConfiguration.class,
      MybatisPlusAutoConfiguration.class
    })
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "DOCKER_HOST", matches = ".*")
@DisplayName("SystemUserMapper 集成测试")
class SystemUserMapperTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("ai_agent_test")
          .withUsername("test")
          .withPassword("test")
          .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("decorator.datasource.p6spy.enable-logging", () -> "false");
    registry.add("mybatis-plus.mapper-locations", () -> "classpath*:mapper/**/*.xml");
    registry.add("mybatis-plus.type-aliases-package", () -> "com.ai.api.**.entity");
    registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    registry.add("mybatis-plus.global-config.db-config.logic-delete-field", () -> "deleted");
    registry.add("mybatis-plus.global-config.db-config.logic-not-delete-value", () -> "0");
    registry.add("mybatis-plus.global-config.db-config.logic-delete-value", () -> "1");
  }

  @Autowired private SystemUserMapper SystemUserMapper;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void setUp() throws Exception {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS `sys_user`");
    ScriptUtils.executeSqlScript(
        dataSource.getConnection(),
        new ClassPathResource("db/migration/V20260819__create_sys_user.sql"));
    // 镜像 V20260903 迁移：salt 列为 BCrypt 冗余字段，已删除
    jdbcTemplate.execute("ALTER TABLE `sys_user` DROP COLUMN `salt`");
    // 镜像 V20260904 迁移：新增令牌版本号列（用于改密码/强制下线后立即作废既有 JWT）
    jdbcTemplate.execute(
        "ALTER TABLE `sys_user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT '令牌版本号'");
  }

  @Test
  @Order(1)
  @DisplayName("insert + selectById 应返回正确数据")
  void insert_andSelectById_shouldReturnData() {
    // given
    SystemUser user = buildUser("alice", "alice@example.com", "hashed-pwd");

    // when
    int rows = SystemUserMapper.insert(user);

    // then
    assertThat(rows).isEqualTo(1);
    assertThat(user.getId()).isNotNull();

    SystemUser loaded = SystemUserMapper.selectById(user.getId());
    assertThat(loaded).isNotNull();
    assertThat(loaded.getUsername()).isEqualTo("alice");
    assertThat(loaded.getEmail()).isEqualTo("alice@example.com");
    assertThat(loaded.getPasswordHash()).isEqualTo("hashed-pwd");
    assertThat(loaded.getStatus()).isEqualTo(1);
    assertThat(loaded.getFailCount()).isZero();
    assertThat(loaded.getTokenVersion()).isZero();
    assertThat(loaded.getDeleted()).isZero();
    assertThat(loaded.getCreator()).isEqualTo("system");
    assertThat(loaded.getCreateTime()).isNotNull();
  }

  @Test
  @Order(2)
  @DisplayName("selectByUsername 应按用户名精确查询")
  void selectByUsername_shouldReturnUser() {
    // given
    SystemUserMapper.insert(buildUser("bob", "bob@example.com", "pwd"));

    // when
    SystemUser found = SystemUserMapper.selectByUsername("bob");
    SystemUser notFound = SystemUserMapper.selectByUsername("nonexistent");

    // then
    assertThat(found).isNotNull();
    assertThat(found.getUsername()).isEqualTo("bob");
    assertThat(notFound).isNull();
  }

  @Test
  @Order(3)
  @DisplayName("selectByEmail 应按邮箱精确查询")
  void selectByEmail_shouldReturnUser() {
    // given
    SystemUserMapper.insert(buildUser("carol", "carol@example.com", "pwd"));

    // when
    SystemUser found = SystemUserMapper.selectByEmail("carol@example.com");
    SystemUser notFound = SystemUserMapper.selectByEmail("nobody@example.com");

    // then
    assertThat(found).isNotNull();
    assertThat(found.getEmail()).isEqualTo("carol@example.com");
    assertThat(notFound).isNull();
  }

  @Test
  @Order(4)
  @DisplayName("updateById 应正确更新字段")
  void updateById_shouldUpdateFields() {
    // given
    SystemUser user = buildUser("dave", "dave@example.com", "pwd");
    SystemUserMapper.insert(user);

    // when
    user.setFailCount(3);
    user.setLockUntil(LocalDateTime.now().plusMinutes(15));
    user.setLastLoginAt(LocalDateTime.now());
    int rows = SystemUserMapper.updateById(user);

    // then
    assertThat(rows).isEqualTo(1);
    SystemUser loaded = SystemUserMapper.selectById(user.getId());
    assertThat(loaded.getFailCount()).isEqualTo(3);
    assertThat(loaded.getLockUntil()).isNotNull();
    assertThat(loaded.getLastLoginAt()).isNotNull();
  }

  @Test
  @Order(5)
  @DisplayName("deleteById（逻辑删除）后 selectById 应返回 null")
  void deleteById_shouldLogicallyDelete() {
    // given
    SystemUser user = buildUser("eve", "eve@example.com", "pwd");
    SystemUserMapper.insert(user);
    Long id = user.getId();

    // when
    int rows = SystemUserMapper.deleteById(id);

    // then
    assertThat(rows).isEqualTo(1);
    assertThat(SystemUserMapper.selectById(id)).isNull();
  }

  @Test
  @Order(6)
  @DisplayName("selectPage 应正确分页")
  void selectPage_shouldReturnPagedResult() {
    // given
    for (int i = 0; i < 10; i++) {
      SystemUserMapper.insert(buildUser("user" + i, "user" + i + "@example.com", "pwd"));
    }

    // when
    Page<SystemUser> page = new Page<>(1, 5);
    Page<SystemUser> result = SystemUserMapper.selectPage(page, new QueryWrapper<>());

    // then
    assertThat(result.getRecords()).hasSize(5);
    assertThat(result.getTotal()).isEqualTo(10);
    assertThat(result.getPages()).isEqualTo(2);
  }

  @Test
  @Order(7)
  @DisplayName("状态查询：selectList 应能过滤 status=1 的正常用户")
  void selectList_shouldFilterByStatus() {
    // given
    SystemUserMapper.insert(buildUser("active1", "a1@example.com", "pwd"));
    SystemUser inactive = buildUser("inactive1", "i1@example.com", "pwd");
    inactive.setStatus(0);
    SystemUserMapper.insert(inactive);

    // when
    List<SystemUser> activeUsers =
        SystemUserMapper.selectList(new QueryWrapper<SystemUser>().eq("status", 1));

    // then
    assertThat(activeUsers).hasSize(1);
    assertThat(activeUsers.get(0).getUsername()).isEqualTo("active1");
  }

  @Test
  @Order(8)
  @DisplayName("incrementTokenVersion 应原子自增令牌版本号")
  void incrementTokenVersion_shouldIncrementVersion() {
    // given
    SystemUser user = buildUser("frank", "frank@example.com", "pwd");
    SystemUserMapper.insert(user);

    // when
    int first = SystemUserMapper.incrementTokenVersion(user.getId());
    int second = SystemUserMapper.incrementTokenVersion(user.getId());

    // then
    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(1);
    SystemUser loaded = SystemUserMapper.selectById(user.getId());
    assertThat(loaded.getTokenVersion()).isEqualTo(2);
  }

  private SystemUser buildUser(String username, String email, String pwdHash) {
    SystemUser user = new SystemUser();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(pwdHash);
    user.setStatus(1);
    user.setFailCount(0);
    return user;
  }
}
