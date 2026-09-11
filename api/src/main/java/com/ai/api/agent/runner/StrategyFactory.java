package com.ai.api.agent.runner;

import com.ai.api.common.exception.BusinessException;
import com.ai.api.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runner 工厂（add-agent-runner-pipeline）。
 *
 * <p>按 Agent 配置策略动态选择执行器：
 *
 * <ul>
 *   <li>{@link AgentStrategy#COT} → {@link CotAgentRunner}（ReAct 文本解析）
 *   <li>{@link AgentStrategy#FC} → {@link FcAgentRunner}（模型原生 Function Calling）
 * </ul>
 *
 * <p>启动时基于各 Runner 自声明的 {@link AgentRunner#strategy()} 构建策略 → 实现映射。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyFactory {

  private final List<AgentRunner> runners;

  private final Map<AgentStrategy, AgentRunner> strategyMap = new EnumMap<>(AgentStrategy.class);

  @PostConstruct
  void init() {
    for (AgentRunner runner : runners) {
      strategyMap.put(runner.strategy(), runner);
      log.info(
          "[StrategyFactory][init][strategy({}) → runner({})]",
          runner.strategy(),
          runner.getClass().getSimpleName());
    }
  }

  /**
   * 按策略获取执行器。
   *
   * @param strategy 执行策略
   * @return AgentRunner 实现
   * @throws BusinessException 无对应实现时抛 {@link ErrorCode#AGENT_STRATEGY_NOT_SUPPORTED}
   */
  public AgentRunner getRunner(AgentStrategy strategy) {
    AgentRunner runner = strategyMap.get(strategy);
    if (runner == null) {
      throw new BusinessException(
          ErrorCode.AGENT_STRATEGY_NOT_SUPPORTED, "no runner for strategy: " + strategy);
    }
    return runner;
  }

  /** 按策略名（大小写不敏感）获取执行器。 */
  public AgentRunner getRunner(String strategyName) {
    try {
      return getRunner(AgentStrategy.valueOf(strategyName.trim().toUpperCase()));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          ErrorCode.AGENT_STRATEGY_NOT_SUPPORTED, "unknown strategy: " + strategyName);
    }
  }
}
