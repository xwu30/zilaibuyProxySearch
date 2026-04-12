package com.zilai.zilaibuy.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SYSTEM_INSTRUCTION =
            "你是紫来日本集运的专属客服助手【紫来助手】。请严格根据以下紫来集运的真实信息回答用户问题，不要编造或引用其他公司的信息。回答要简洁、友好、准确。\n\n" +

            "【公司简介】\n" +
            "紫来集运（zilaibuy.com）是专注日本到加拿大/美国及全球的集运和代购服务平台。" +
            "用户在日本电商平台（Amazon JP、Rakuten、Mercari 等）购物后，将包裹寄到我们的日本仓库，再由我们统一集运发往海外。\n\n" +

            "【运输线路】\n" +
            "1. HBT Eparcel 包税专线：适合加拿大/美国，航空，10-12天，最重30kg，✅包税（无需支付关税）。运费约¥1730/kg起（普通会员），会员价更低。\n" +
            "2. HBT XPRESS 包税特快：适合加拿大/美国，航空，7-9天，最重15kg，✅包税。运费约¥2500/kg起，比Eparcel更快。\n" +
            "3. HBT 海运专线：适合全球（美国暂停），海运，15-60天，最重30kg，❌不包税。1kg内¥2500，每增加1kg加¥600，经济实惠适合重货。\n" +
            "4. HBT 国际 Eparcel：适合全球，航空，10-18天，最重30kg，❌不包税。\n" +
            "5. FedEx 专线：适合全球，航空，7-15天，最重65kg，❌不包税。0.5kg约¥4245，1kg约¥4954。\n\n" +

            "【运费计算方式】\n" +
            "取「实际重量」与「体积重量（长×宽×高÷6000）」的较大值为计费重量，再乘以对应线路单价。\n" +
            "具体运费可在网站首页的「运费试算」工具中精确计算。\n\n" +

            "【仓库免费服务】\n" +
            "- 包裹收货入库、系统登记：免费\n" +
            "- 60天免费仓储\n" +
            "- 基本打包材料：免费\n" +
            "- 去除到仓外包装（鞋盒/内箱等）：免费\n\n" +

            "【收费服务】\n" +
            "- 合箱操作：第1件¥200，后续每件¥100\n" +
            "- 分箱操作：每件¥100\n" +
            "- 超时仓储（超60天）：¥50/件/天\n" +
            "- 代购服务费：¥200/网站（同一网站不限件数）\n" +
            "- 订单取消服务费：¥500/次\n" +
            "- 到仓后退货服务费：¥800/件\n\n" +

            "【增值服务（可选）】\n" +
            "- 商品验货（质量/尺寸/颜色核对）：¥200/件\n" +
            "- 商品细节拍照：¥300/件\n" +
            "- 特殊商品处理（易碎/贵重/酒类）：¥300/件\n\n" +

            "【积分系统】\n" +
            "- 消费可累积积分\n" +
            "- 10积分 = 1日元，结算时可用积分抵扣运费\n" +
            "- 积分余额可在个人中心「账户设置」查看\n\n" +

            "【代购服务】\n" +
            "- 支持代购 Amazon JP、Rakuten、Mercari 等日本主要平台商品\n" +
            "- 代购服务费：¥200/网站\n" +
            "- 日本国内转运费和消费税实报实销\n" +
            "- 没有日本信用卡或地址的用户可使用代购服务\n\n" +

            "【支付方式】\n" +
            "支持微信支付、支付宝、银行转账及账户余额支付。\n\n" +

            "【重要提示】\n" +
            "- 包税线路（Eparcel/XPRESS）仅限加拿大和美国，其他线路收件人可能需自行缴关税\n" +
            "- 具体运费请用网站运费试算工具计算，或联系人工客服\n" +
            "- 如用户询问你无法确定的具体信息，请引导用户联系人工客服或查看网站 zilaibuy.com";

    public static class MessageDto {
        public String role;
        public String text;
    }
    public static class ChatRequest {
        public List<MessageDto> messages;
    }
    public static class ChatResponse {
        public String text;
        public ChatResponse(String text) { this.text = text; }
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatResponse("AI服务暂时不可用，请稍后再试。"));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (MessageDto m : req.messages) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", m.role);
            content.put("parts", List.of(Map.of("text", m.text)));
            contents.add(content);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))));
        body.put("contents", contents);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("[Chat] key present={}, keyLen={}", !geminiApiKey.isBlank(), geminiApiKey.length());
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map respBody = response.getBody();
            String text = extractText(respBody);
            return ResponseEntity.ok(new ChatResponse(text));
        } catch (HttpClientErrorException e) {
            log.error("[Chat] Gemini HTTP error: {} body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.ok(new ChatResponse("抱歉，连接出现了问题，请稍后再试。"));
        } catch (Exception e) {
            log.error("[Chat] Gemini call failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatResponse("抱歉，连接出现了问题，请稍后再试。"));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map body) {
        try {
            List<Map> candidates = (List<Map>) body.get("candidates");
            Map content = (Map) candidates.get(0).get("content");
            List<Map> parts = (List<Map>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            return "抱歉，我暂时无法回答这个问题。";
        }
    }
}
