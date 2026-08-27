-- 掌上律师 MVP - 测试数据

-- ========== 测试用户 ==========
INSERT INTO app_user (openid, nickname, avatar_url) VALUES 
('test_openid_001', '测试用户1', 'https://example.com/avatar1.jpg'),
('test_openid_002', '测试用户2', 'https://example.com/avatar2.jpg');

-- ========== 测试案件 ==========
INSERT INTO case_profile (user_id, domain_type, status, title, facts_json, issues_json, laws_json, timeline_json, evidence_refs) VALUES 
(1, 'LABOR', 'COLLECTING', '违法解除劳动合同纠纷', 
 '{"hasWrittenContract": {"value": true, "confidence": "HIGH"}, "salaryAmount": {"value": 15000, "confidence": "HIGH"}}',
 '["违法解除劳动合同", "经济补偿金计算"]',
 '[]',
 '[]',
 '[]'),
(1, 'LABOR', 'ANALYZING', '拖欠工资纠纷', 
 '{"hasWrittenContract": {"value": true, "confidence": "HIGH"}, "salaryAmount": {"value": 12000, "confidence": "HIGH"}, "terminationType": {"value": "仍在职", "confidence": "HIGH"}}',
 '["拖欠工资", "经济补偿金"]',
 '[]',
 '[]',
 '[]');

-- ========== 测试对话会话 ==========
INSERT INTO chat_session (case_id, user_id, summary) VALUES 
(1, 1, '违法解除劳动合同咨询'),
(2, 1, '拖欠工资咨询');

-- ========== 测试对话消息 ==========
INSERT INTO chat_message (session_id, role, content) VALUES 
(1, 'user', '我被公司辞退了，没有提前通知，也没有给经济补偿金，我该怎么办？'),
(1, 'assistant', '根据《劳动合同法》第47条和第87条的规定，用人单位违法解除劳动合同，应当支付赔偿金。赔偿金的标准是经济补偿金的二倍。请问您在公司工作了多长时间？'),
(1, 'user', '我在公司工作了3年'),
(1, 'assistant', '根据您的情况，您可以主张违法解除劳动合同赔偿金。计算方式：3年 × 15000元/月 × 2倍 = 90000元。建议您收集以下证据：1. 劳动合同；2. 工资流水；3. 辞退通知；4. 考勤记录。'),

(2, 'user', '公司已经3个月没发工资了，我该怎么办？'),
(2, 'assistant', '根据《劳动合同法》第30条和第85条的规定，用人单位未及时足额支付劳动报酬的，劳动者可以依法向当地人民法院申请支付令。建议您：1. 收集工资流水证明欠薪事实；2. 向劳动监察大队投诉；3. 申请劳动仲裁。');

-- ========== 测试法律缓存 ==========
INSERT INTO law_cache (cache_key, source, response_json, expires_at) VALUES 
('law_search_test_001', 'lawstar', '{"results": [{"law_title": "劳动合同法", "article": "第47条", "content": "经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。"}]}', DATEADD('DAY', 1, CURRENT_TIMESTAMP));

-- ========== 测试向量知识库 ==========
INSERT INTO domain_knowledge_vector (domain_type, content_type, content_id, text_chunk, metadata_json) VALUES 
('LABOR', 'LAW_ARTICLE', 'labor_contract_law_47', '《劳动合同法》第47条：经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。六个月以上不满一年的，按一年计算；不满六个月的，向劳动者支付半个月工资的经济补偿。', '{"law_title": "劳动合同法", "article": "第47条"}'),
('LABOR', 'LAW_ARTICLE', 'labor_contract_law_87', '《劳动合同法》第87条：用人单位违反本法规定解除或者终止劳动合同的，应当依照本法第四十七条规定的经济补偿标准的二倍向劳动者支付赔偿金。', '{"law_title": "劳动合同法", "article": "第87条"}');

-- ========== 说明 ==========
-- 测试数据说明：
-- 1. 包含2个测试用户
-- 2. 包含2个测试案件（违法解除、拖欠工资）
-- 3. 包含对话历史记录
-- 4. 包含法律缓存示例
-- 5. 包含向量知识库示例
