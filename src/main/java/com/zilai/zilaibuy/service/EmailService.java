package com.zilai.zilaibuy.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.mail.from-name:紫来买 ZilaiBuy}")
    private String fromName;

    @Value("${app.mail.reply-to:support@zilaibuy.com}")
    private String replyTo;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Send a multipart (HTML + plain text) email with proper From display name,
     * Reply-To and List-Unsubscribe headers so Gmail / Outlook treat it as legit
     * transactional mail. Body is supplied as plain text; an HTML version is
     * generated automatically by wrapping the text in a simple template.
     */
    private boolean sendMail(String toEmail, String subject, String plainBody) {
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Mail not sent (no SMTP credentials): to={} subject={}", toEmail, subject);
            return false;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
            helper.setReplyTo(replyTo);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainBody, wrapHtml(subject, plainBody));

            // Help Gmail / Outlook identify this as legitimate transactional mail.
            mime.setHeader("List-Unsubscribe",
                    "<mailto:" + replyTo + "?subject=unsubscribe>, <" + frontendUrl + "/unsubscribe>");
            mime.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            mime.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
            mime.setHeader("Auto-Submitted", "auto-generated");

            mailSender.send(mime);
            log.info("Mail sent to {} subject=[{}]", toEmail, subject);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send mail to {} subject=[{}] ({})", toEmail, subject, e.getMessage());
            return false;
        }
    }

    private String wrapHtml(String subject, String plainBody) {
        String escaped = plainBody
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        // Auto-link http(s) URLs.
        escaped = escaped.replaceAll("(https?://[\\w\\-./?=&%#:+]+)",
                "<a href=\"$1\" style=\"color:#7c3aed;text-decoration:none;\">$1</a>");
        // Preserve line breaks.
        String body = escaped.replace("\n", "<br>");
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escapeHtml(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f5f5f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;color:#1f2937;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f5f5f7;padding:24px 0;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.05);\">"
                + "<tr><td style=\"padding:24px 32px;border-bottom:1px solid #f0f0f3;\">"
                + "<div style=\"font-size:18px;font-weight:700;color:#111827;\">紫来买 · ZilaiBuy</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;font-size:14px;line-height:1.7;color:#1f2937;\">"
                + body
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background:#fafafa;font-size:11px;color:#9ca3af;line-height:1.6;\">"
                + "本邮件由系统自动发送，请勿直接回复。<br>"
                + "如需联系我们，请发送邮件至 <a href=\"mailto:" + replyTo + "\" style=\"color:#7c3aed;text-decoration:none;\">" + replyTo + "</a><br>"
                + "© 2026 ZilaiBuy. <a href=\"" + frontendUrl + "\" style=\"color:#9ca3af;text-decoration:none;\">zilaibuy.com</a>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean sendOtpEmail(String toEmail, String code) {
        return sendMail(toEmail, "【ZilaiBuy】您的注册验证码",
                "您好！\n\n您的注册验证码为：" + code + "\n\n验证码5分钟内有效，请勿泄露给他人。\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = frontendUrl + "/?resetToken=" + token;
        sendMail(toEmail, "【ZilaiBuy】密码重置",
                "您好！\n\n您申请了重置密码。请点击以下链接设置新密码（1小时内有效）：\n\n" + link
                        + "\n\n如非本人操作，请忽略此邮件，您的账户安全不受影响。\n\n— ZilaiBuy 团队");
    }

    public void sendParcelCheckinEmail(String toEmail, String displayName,
                                       String trackingNo, String inboundCode, String location) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您的包裹已成功到达紫来买仓库，详情如下：\n\n" +
                "  快递单号：" + trackingNo + "\n" +
                (inboundCode != null && !inboundCode.isBlank() ? "  入库编号：" + inboundCode + "\n" : "") +
                (location != null && !location.isBlank() ? "  存放位置：" + location + "\n" : "") +
                "\n您可以登录紫来买查看包裹状态，并提交转运申请：" + frontendUrl + "\n\n" +
                "— 紫来买团队";
        sendMail(toEmail, "【紫来买】您的包裹已到库 " + (inboundCode != null ? inboundCode : trackingNo), body);
    }

    public void sendStorageReminderEmail(String toEmail, String displayName,
                                         String inboundCode, long daysStored) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您的包裹（入库编号：" + inboundCode + "）已在我们仓库存放超过 " + daysStored + " 天。\n\n" +
                "根据我们的收费政策，超出免费存储期后将产生仓储费用：\n" +
                "  • 第1-60天：免费\n" +
                "  • 第61-90天：¥50/件/天\n" +
                "  • 第91-180天：¥100/件/天\n" +
                "  • 第181天起：¥100/件/天\n\n" +
                "请尽快登录 ZilaiBuy 提交转运申请，以避免产生额外费用。\n\n" +
                "— ZilaiBuy 团队";
        sendMail(toEmail, "【ZilaiBuy】您的包裹已在仓库超过60天，请尽快安排发货", body);
    }

    public void sendPaymentReminderEmail(String toEmail, String displayName,
                                         String orderNo, String feeDetails) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您的订单（" + orderNo + "）已完成称重打包，运费已确认，请尽快登录紫来买完成付款。\n\n" +
                (feeDetails != null && !feeDetails.isBlank() ? feeDetails + "\n\n" : "") +
                "立即前往支付：" + frontendUrl + "\n" +
                "（登录后在个人中心 → 待支付包裹 中找到您的订单）\n\n" +
                "请及时完成付款，以便我们尽快为您安排发货。付款后我们会第一时间将包裹发出。\n\n" +
                "如有任何问题，欢迎联系我们的客服。\n\n" +
                "— 紫来买团队";
        sendMail(toEmail, "【紫来买】您的包裹已打包完成，请尽快支付运费 " + orderNo, body);
    }

    public void sendShippedEmail(String toEmail, String displayName,
                                  String orderNo, String trackingNo, String carrier) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您的订单（" + orderNo + "）已从我们仓库发出，请使用以下信息查询物流：\n\n" +
                "  末端单号：" + (trackingNo != null ? trackingNo : "—") + "\n" +
                (carrier != null && !carrier.isBlank() ? "  物流商：" + carrier + "\n" : "") +
                "\n请登录 ZilaiBuy 查看最新物流状态。\n\n" +
                "— ZilaiBuy 团队";
        sendMail(toEmail, "【ZilaiBuy】您的包裹已发出 " + orderNo, body);
    }

    public void sendProxyOrderNotification(String adminEmail, String customerName, String customerPhone,
                                           String customerEmail, com.zilai.zilaibuy.entity.OrderEntity order) {
        StringBuilder sb = new StringBuilder();
        sb.append("收到新的采购订单：\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("客户信息\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("姓名：").append(customerName).append("\n");
        sb.append("手机：").append(customerPhone).append("\n");
        if (StringUtils.hasText(customerEmail)) sb.append("邮箱：").append(customerEmail).append("\n");
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("订单信息\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("订单号：").append(order.getOrderNo()).append("\n");
        sb.append("订单合计：¥").append(order.getTotalCny()).append(" CNY\n");
        if (order.getNotes() != null && !order.getNotes().isBlank())
            sb.append("备注：").append(order.getNotes()).append("\n");
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("商品明细\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        for (com.zilai.zilaibuy.entity.OrderItemEntity item : order.getItems()) {
            sb.append("· ").append(item.getProductTitle())
              .append(" x").append(item.getQuantity())
              .append(" ¥").append(item.getPriceCny()).append(" CNY");
            if (item.getPlatform() != null && !item.getPlatform().isBlank())
                sb.append(" [").append(item.getPlatform()).append("]");
            sb.append("\n");
            if (item.getOriginalUrl() != null && !item.getOriginalUrl().isBlank())
                sb.append("  链接：").append(item.getOriginalUrl()).append("\n");
            if (item.getRemarks() != null && !item.getRemarks().isBlank())
                sb.append("  备注：").append(item.getRemarks()).append("\n");
        }
        sb.append("\n— ZilaiBuy 系统通知");
        sendMail(adminEmail, "【ZilaiBuy】新采购订单 " + order.getOrderNo() + " - " + customerName, sb.toString());
    }

    public void sendVasRequestNotification(String adminEmail, String customerName, String customerPhone,
                                           String customerEmail, String contactInfo, String items, String services) {
        String body = "收到新的增值服务申请：\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "客户信息\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "姓名：" + customerName + "\n" +
                "手机：" + customerPhone + "\n" +
                (StringUtils.hasText(customerEmail) ? "邮箱：" + customerEmail + "\n" : "") +
                (StringUtils.hasText(contactInfo) ? "联系方式：" + contactInfo + "\n" : "") +
                "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "申请服务\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                services + "\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "涉及包裹 / 订单\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                items + "\n" +
                "— ZilaiBuy 系统通知";
        sendMail(adminEmail, "【ZilaiBuy】增值服务申请 - " + customerName, body);
    }

    public void sendVasCompletionEmail(String toEmail, String displayName, String services, String itemsSummary, String adminNotes) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您申请的增值服务已处理完成：\n\n" +
                "  服务项目：" + services + "\n" +
                (itemsSummary != null && !itemsSummary.isBlank() ? "  涉及包裹/订单：\n" + itemsSummary + "\n" : "") +
                (adminNotes != null && !adminNotes.isBlank() ? "\n  仓库备注：" + adminNotes + "\n" : "") +
                "\n请登录 ZilaiBuy 个人中心查看详情和照片。\n\n" +
                "— ZilaiBuy 团队";
        sendMail(toEmail, "【ZilaiBuy】您的增值服务已完成", body);
    }

    public void sendCustomVasQuoteEmail(String toEmail, String displayName, String description,
                                       Integer quoteJpy, String adminNotes) {
        if (!StringUtils.hasText(toEmail)) return;
        String body = "您好，" + displayName + "！\n\n" +
                "您提交的增值任务已收到仓库报价，详情如下：\n\n" +
                "  任务描述：" + description + "\n" +
                (quoteJpy != null ? "  报价金额：¥" + quoteJpy + " JPY\n" : "") +
                (adminNotes != null && !adminNotes.isBlank() ? "  仓库备注：" + adminNotes + "\n" : "") +
                "\n请登录紫来买个人中心查看并确认。\n\n" +
                "— 紫来买团队";
        sendMail(toEmail, "【紫来买】您的自定义增值任务已收到报价", body);
    }

    public void sendConfirmationEmail(String toEmail, String token) {
        String link = baseUrl + "/api/auth/confirm-email?token=" + token;
        sendMail(toEmail, "【ZilaiBuy】请确认您的邮箱",
                "您好！\n\n请点击以下链接完成邮箱验证（24小时内有效）：\n\n" + link + "\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
    }

    public void sendWarehouseDispatchEmail(String toEmail, com.zilai.zilaibuy.entity.OrderEntity order) {
        if (!StringUtils.hasText(toEmail)) return;
        StringBuilder sb = new StringBuilder();
        sb.append("订单号：").append(order.getOrderNo()).append("\n");
        if (order.getPackingNo() != null) sb.append("转运单号：").append(order.getPackingNo()).append("\n");
        if (order.getRequestedShippingLineName() != null) sb.append("线路：").append(order.getRequestedShippingLineName()).append("\n");
        else if (order.getRequestedShippingLine() != null) sb.append("线路：").append(order.getRequestedShippingLine()).append("\n");
        if (order.getQuotedFeeJpy() != null) sb.append("运费：¥").append(order.getQuotedFeeJpy()).append(" JPY\n");
        if (order.getWeightG() != null) sb.append("重量：").append(String.format("%.3f kg", order.getWeightG() / 1000.0)).append("\n");
        if (order.getReceiverAddress() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<?, ?> addr = om.readValue(order.getReceiverAddress(), java.util.Map.class);
                sb.append("\n收件人信息：\n");
                if (addr.get("fullName") != null) sb.append("  姓名：").append(addr.get("fullName")).append("\n");
                if (addr.get("phone") != null) sb.append("  电话：").append(addr.get("phone")).append("\n");
                if (addr.get("street") != null) sb.append("  地址：").append(addr.get("street")).append(", ").append(addr.get("city")).append(", ").append(addr.get("province")).append(" ").append(addr.get("postalCode")).append(", ").append(addr.get("country")).append("\n");
            } catch (Exception ignored) {}
        }
        sb.append("\n客户已完成付款，请尽快安排发货。\n\n— 紫来买系统");
        sendMail(toEmail, "【紫来买】运费已付款，请安排发货 " + order.getOrderNo(), sb.toString());
    }
}
