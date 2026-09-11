package com.ai.api.cs.intent;

import com.ai.api.cs.entity.CsIntent;
import com.ai.api.cs.mapper.CsIntentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 意图注册表初始化器（add-customer-service）。
 *
 * <p>应用启动时检查 {@code cs_intent} 表，5 类内置意图不存在则插入。
 *
 * @author add-customer-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRegistry implements ApplicationRunner {

  private final CsIntentMapper csIntentMapper;

  /** 内置意图：name → {name, description, slotsSchema, fallbackIntent, keywords}。 */
  private static final String[][] BUILTIN_INTENTS = {
    {
      "sales_query",
      "销量查询",
      "{\"region\":{\"required\":true},\"product\":{\"required\":true},"
          + "\"time_range\":{\"required\":false}}",
      "small_talk",
      "销量,销售,卖了多少,销量查询,销量统计,销售额,成交,出货量,销售数据,月度销量,销量排名"
    },
    {
      "product_compare",
      "产品对比",
      "{\"products\":{\"required\":true,\"type\":\"array\"},"
          + "\"dimension\":{\"required\":false}}",
      "small_talk",
      "对比,比较,哪个好,差异,vs,区别,优缺点,性价比,差异对比"
    },
    {
      "reimbursement_standard",
      "报销标准咨询",
      "{\"category\":{\"required\":true}}",
      "small_talk",
      "报销,报销标准,差旅,费用报销,报销额度,报销比例,差旅费,补贴标准"
    },
    {
      "fault_diagnosis",
      "故障排查",
      "{\"product\":{\"required\":true},\"symptom\":{\"required\":true}}",
      "small_talk",
      "故障,报错,异常,无法使用,坏了,崩溃,无法启动,闪退,卡死,白屏,连不上,无法登录"
    },
    {"small_talk", "闲聊", "{}", "small_talk", "你好,在吗,谢谢,再见,早上好,下午好,晚上好,hello,hi,拜拜,辛苦"},
    {
      "order_query",
      "订单查询",
      "{\"customer_name\":{\"required\":true},\"order_no\":{\"required\":false}}",
      "small_talk",
      "订单,物流,快递,发货,包裹,单号,查单,查物流,到哪了,签收,配送,我的订单"
    },
    {
      "refund_return",
      "退换货",
      "{\"order_no\":{\"required\":true},\"reason\":{\"required\":false}}",
      "small_talk",
      "退货,退款,换货,退单,申请退,七天无理由,退货退款,退换货"
    },
    {
      "payment_bill",
      "支付账单",
      "{\"customer_name\":{\"required\":true},\"bill_type\":{\"required\":false}}",
      "small_talk",
      "支付,账单,缴费,扣款,发票,开票,对账,费用明细"
    },
    {
      "complaint_after_sale",
      "售后投诉",
      "{\"issue\":{\"required\":false}}",
      "small_talk",
      "投诉,售后,索赔,举报,差评,客服电话"
    },
    {
      "account_management",
      "账号管理",
      "{\"account\":{\"required\":true},\"action\":{\"required\":false}}",
      "small_talk",
      "登录,密码,账号,注册,改密,忘记密码,验证码,注销"
    }
  };

  @Override
  public void run(ApplicationArguments args) {
    int inserted = 0;
    for (String[] meta : BUILTIN_INTENTS) {
      String name = meta[0];
      LambdaQueryWrapper<CsIntent> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(CsIntent::getName, name);
      CsIntent existing = csIntentMapper.selectOne(wrapper);
      if (existing != null) continue;

      CsIntent intent = new CsIntent();
      intent.setName(name);
      intent.setDescription(meta[1]);
      intent.setSlotsSchema(meta[2]);
      intent.setFallbackIntent(meta[3]);
      intent.setKeywords(meta[4]);
      intent.setEnabled(1);
      csIntentMapper.insert(intent);
      inserted++;
    }
    log.info(
        "[IntentRegistry][init intents: inserted({}) total({})]", inserted, BUILTIN_INTENTS.length);
  }
}
