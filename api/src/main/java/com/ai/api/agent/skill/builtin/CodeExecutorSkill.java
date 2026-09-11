package com.ai.api.agent.skill.builtin;

import com.ai.api.agent.config.AgentProperties;
import com.ai.api.agent.skill.Skill;
import com.ai.api.agent.skill.SkillType;
import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码执行 Skill（add-agent-runtime，Docker 沙箱）。
 *
 * <p>通过 {@code docker run} 在独立容器内执行 Python 代码，保证主进程隔离：
 *
 * <ul>
 *   <li>镜像：{@code agent.sandbox.image}（默认 python:3.11-slim）
 *   <li>容器参数：{@code --read-only --security-opt no-new-privileges --network=none}
 *   <li>超时：{@code agent.sandbox.timeout}（默认 10s），超时由 Docker 强制 kill
 * </ul>
 *
 * <p>若 Docker 不可用（agent.sandbox.enabled=false），抛 SKILL_NOT_IMPLEMENTED。
 *
 * @author add-agent-runtime
 */
@Slf4j
@Component("codeExecutorSkill")
@RequiredArgsConstructor
public class CodeExecutorSkill {

  private final AgentProperties agentProperties;

  @Skill(name = "code_executor", type = SkillType.LOCAL, description = "Python 沙箱执行（Docker 隔离）")
  public Map<String, Object> execute(Map<String, Object> input) {
    if (!agentProperties.getSandbox().isEnabled()) {
      throw new BusinessException(
          ErrorCode.SKILL_NOT_IMPLEMENTED, "sandbox disabled, code_executor unavailable");
    }

    Object codeObj = input.get("code");
    Object langObj = input.get("lang");
    String lang = langObj == null ? "python" : langObj.toString().toLowerCase();
    if (!"python".equals(lang) && !"python3".equals(lang)) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "only python is supported, got: " + lang);
      return err;
    }
    if (codeObj == null) {
      Map<String, Object> err = new HashMap<>();
      err.put("error", "code is required");
      return err;
    }
    String code = codeObj.toString();
    int timeout = agentProperties.getSandbox().getTimeout();
    String image = agentProperties.getSandbox().getImage();

    List<String> cmd = new ArrayList<>();
    cmd.add("docker");
    cmd.add("run");
    cmd.add("--rm");
    cmd.add("--read-only");
    cmd.add("--security-opt=no-new-privileges");
    cmd.add("--network=none");
    cmd.add("--memory=128m");
    cmd.add("--cpus=0.5");
    cmd.add("-i");
    cmd.add(image);
    cmd.add("python");
    cmd.add("-c");
    cmd.add(code);

    Map<String, Object> result = new HashMap<>();
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
      boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
      if (!finished) {
        p.destroyForcibly();
        result.put("error", "execution timeout after " + timeout + "s");
        result.put("exitCode", -1);
        return result;
      }
      String stdout = readAll(p.getInputStream());
      String stderr = readAll(p.getErrorStream());
      result.put("stdout", stdout);
      result.put("stderr", stderr);
      result.put("exitCode", p.exitValue());
      return result;
    } catch (Exception e) {
      log.error("[CodeExecutorSkill][execute][Docker 调用失败]", e);
      throw new BusinessException(
          ErrorCode.SKILL_INVOKE_FAILED, "docker exec failed: " + e.getMessage());
    }
  }

  private String readAll(java.io.InputStream is) throws java.io.IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(line);
      }
    }
    return sb.toString();
  }
}
