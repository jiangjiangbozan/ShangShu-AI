-- ============================================================
-- 客服意图关键词增强（意图识别关键词规则配置化 + 意图库扩充）
-- 1. cs_intent 增加 keywords 列（触发关键词，逗号分隔，命中即规则判定）
-- 2. 为存量 5 类意图补充关键词（同义词/词形变体，运营可热更新）
-- 3. 新增 5 类业务意图：订单查询 / 退换货 / 支付账单 / 售后投诉 / 账号管理
-- ============================================================

ALTER TABLE cs_intent
    ADD COLUMN keywords VARCHAR(1000) NULL COMMENT '触发关键词列表（逗号分隔，命中即规则判定）' AFTER slots_schema;

UPDATE cs_intent SET keywords = '销量,销售,卖了多少,销量查询,销量统计,销售额,成交,出货量,销售数据,月度销量,销量排名' WHERE name = 'sales_query';
UPDATE cs_intent SET keywords = '对比,比较,哪个好,差异,vs,区别,优缺点,性价比,差异对比' WHERE name = 'product_compare';
UPDATE cs_intent SET keywords = '报销,报销标准,差旅,费用报销,报销额度,报销比例,差旅费,补贴标准' WHERE name = 'reimbursement_standard';
UPDATE cs_intent SET keywords = '故障,报错,异常,无法使用,坏了,崩溃,无法启动,闪退,卡死,白屏,连不上,无法登录' WHERE name = 'fault_diagnosis';
UPDATE cs_intent SET keywords = '你好,在吗,谢谢,再见,早上好,下午好,晚上好,hello,hi,拜拜,辛苦' WHERE name = 'small_talk';

INSERT INTO cs_intent (name, description, slots_schema, fallback_intent, keywords) VALUES
('order_query', '订单查询', '{"customer_name":{"required":true},"order_no":{"required":false}}', 'small_talk', '订单,物流,快递,发货,包裹,单号,查单,查物流,到哪了,签收,配送,我的订单'),
('refund_return', '退换货', '{"order_no":{"required":true},"reason":{"required":false}}', 'small_talk', '退货,退款,换货,退单,申请退,七天无理由,退货退款,退换货'),
('payment_bill', '支付账单', '{"customer_name":{"required":true},"bill_type":{"required":false}}', 'small_talk', '支付,账单,缴费,扣款,发票,开票,对账,费用明细'),
('complaint_after_sale', '售后投诉', '{"issue":{"required":false}}', 'small_talk', '投诉,售后,索赔,举报,差评,客服电话'),
('account_management', '账号管理', '{"account":{"required":true},"action":{"required":false}}', 'small_talk', '登录,密码,账号,注册,改密,忘记密码,验证码,注销');
