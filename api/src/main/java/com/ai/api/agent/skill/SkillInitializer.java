package com.ai.api.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Skill 启动注册器（add-agent-runtime）。
 *
 * <p>实现 {@link ApplicationRunner}，在应用启动完成后调用 {@link SkillRegistry#register()} 扫描所有 {@code @Skill}
 * 注解方法并 upsert 到 {@code skill_definition} 表。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillInitializer implements ApplicationRunner {

  private final SkillRegistry skillRegistry;

  @Override
  public void run(ApplicationArguments args) {
    skillRegistry.register();
  }
}
