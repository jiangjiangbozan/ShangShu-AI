package com.ai.api.cs.intent;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图识别结果（add-customer-service）。
 *
 * <p>由 {@link IntentClassifierSkill} 输出，包含识别的意图、置信度、需要填充的槽位列表。
 *
 * @author add-customer-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

  /** 识别出的意图名（如 sales_query）。 */
  private String intent;

  /** 置信度 [0,1]。 */
  private double confidence;

  /** 需要填充的槽位名列表（如 region、product）。 */
  private List<String> slotsNeeded;
}
