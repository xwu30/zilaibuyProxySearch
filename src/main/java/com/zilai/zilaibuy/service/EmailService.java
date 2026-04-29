package com.zilai.zilaibuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public boolean sendOtpEmail(String toEmail, String code) {
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Email OTP generated for {}", toEmail);
            log.debug("[DEV] Email OTP for {}: {}", toEmail, code);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】您的注册验证码");
            msg.setText("您好！\n\n您的注册验证码为：" + code + "\n\n验证码5分钟内有效，请勿泄露给他人。\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
            mailSender.send(msg);
            log.info("OTP email sent to {}", toEmail);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send OTP email to {} ({})", toEmail, e.getMessage());
            return false;
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = frontendUrl + "/?resetToken=" + token;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Password reset email generated for {}", toEmail);
            log.debug("[DEV] Password reset link for {}: {}", toEmail, link);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】密码重置");
            msg.setText("您好！\n\n您申请了重置密码。请点击以下链接设置新密码（1小时内有效）：\n\n" + link
                    + "\n\n如非本人操作，请忽略此邮件，您的账户安全不受影响。\n\n— ZilaiBuy 团队");
            mailSender.send(msg);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendParcelCheckinEmail(String toEmail, String displayName,
                                       String trackingNo, String inboundCode, String location) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Parcel checkin email for {} tracking={} code={}", toEmail, trackingNo, inboundCode);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【紫来买】您的包裹已到库 " + (inboundCode != null ? inboundCode : trackingNo));
            msg.setText(
                "您好，" + displayName + "！\n\n" +
                "您的包裹已成功到达紫来买仓库，详情如下：\n\n" +
                "  快递单号：" + trackingNo + "\n" +
                (inboundCode != null && !inboundCode.isBlank() ? "  入库编号：" + inboundCode + "\n" : "") +
                (location != null && !location.isBlank() ? "  存放位置：" + location + "\n" : "") +
                "\n您可以登录紫来买查看包裹状态，并提交转运申请：https://zilaibuy.com\n\n" +
                "— 紫来买团队"
            );
            mailSender.send(msg);
            log.info("Parcel checkin email sent to {} for tracking={}", toEmail, trackingNo);
        } catch (Exception e) {
            log.warn("Failed to send parcel checkin email to {} ({})", toEmail, e.getMessage());
        }
    }

    public void sendStorageReminderEmail(String toEmail, String displayName,
                                         String inboundCode, long daysStored) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Storage reminder email for {} inboundCode={} days={}", toEmail, inboundCode, daysStored);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】您的包裹已在仓库超过60天，请尽快安排发货");
            msg.setText(
                "您好，" + displayName + "！\n\n" +
                "您的包裹（入库编号：" + inboundCode + "）已在我们仓库存放超过 " + daysStored + " 天。\n\n" +
                "根据我们的收费政策，超出免费存储期后将产生仓储费用：\n" +
                "  • 第1-60天：免费\n" +
                "  • 第61-90天：¥50/件/天\n" +
                "  • 第91-180天：¥100/件/天\n" +
                "  • 第181天起：¥100/件/天\n\n" +
                "请尽快登录 ZilaiBuy 提交转运申请，以避免产生额外费用。\n\n" +
                "— ZilaiBuy 团队"
            );
            mailSender.send(msg);
            log.info("Storage reminder email sent to {} for inboundCode={}", toEmail, inboundCode);
        } catch (Exception e) {
            log.warn("Failed to send storage reminder email to {} ({})", toEmail, e.getMessage());
        }
    }

    public void sendPaymentReminderEmail(String toEmail, String displayName,
                                         String orderNo, String feeDetails) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Payment reminder email for {} orderNo={}", toEmail, orderNo);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【紫来买】您的包裹已打包完成，请尽快支付运费 " + orderNo);
            msg.setText(
                "您好，" + displayName + "！\n\n" +
                "您的订单（" + orderNo + "）已完成称重打包，运费已确认，请尽快登录紫来买完成付款。\n\n" +
                (feeDetails != null && !feeDetails.isBlank() ? feeDetails + "\n\n" : "") +
                "立即前往支付：https://zilaibuy.com\n" +
                "（登录后在个人中心 → 待支付包裹 中找到您的订单）\n\n" +
                "请及时完成付款，以便我们尽快为您安排发货。付款后我们会第一时间将包裹发出。\n\n" +
                "如有任何问题，欢迎联系我们的客服。\n\n" +
                "— 紫来买团队"
            );
            mailSender.send(msg);
            log.info("Payment reminder email sent to {} for orderNo={}", toEmail, orderNo);
        } catch (Exception e) {
            log.warn("Failed to send payment reminder email to {} ({})", toEmail, e.getMessage());
        }
    }

    public void sendShippedEmail(String toEmail, String displayName,
                                  String orderNo, String trackingNo, String carrier) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Shipped email for {} orderNo={} tracking={}", toEmail, orderNo, trackingNo);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】您的包裹已发出 " + orderNo);
            msg.setText(
                "您好，" + displayName + "！\n\n" +
                "您的订单（" + orderNo + "）已从我们仓库发出，请使用以下信息查询物流：\n\n" +
                "  末端单号：" + (trackingNo != null ? trackingNo : "—") + "\n" +
                (carrier != null && !carrier.isBlank() ? "  物流商：" + carrier + "\n" : "") +
                "\n请登录 ZilaiBuy 查看最新物流状态。\n\n" +
                "— ZilaiBuy 团队"
            );
            mailSender.send(msg);
            log.info("Shipped email sent to {} for orderNo={} tracking={}", toEmail, orderNo, trackingNo);
        } catch (Exception e) {
            log.warn("Failed to send shipped email to {} ({})", toEmail, e.getMessage());
        }
    }

    public void sendProxyOrderNotification(String adminEmail, String customerName, String customerPhone,
                                           String customerEmail, com.zilai.zilaibuy.entity.OrderEntity order) {
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Proxy order notification for order {}", order.getOrderNo());
            return;
        }
        try {
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

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(adminEmail);
            msg.setSubject("【ZilaiBuy】新采购订单 " + order.getOrderNo() + " - " + customerName);
            msg.setText(sb.toString());
            mailSender.send(msg);
            log.info("Proxy order notification sent to {} for order {}", adminEmail, order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send proxy order notification for {} ({})", order.getOrderNo(), e.getMessage());
        }
    }

    public void sendVasRequestNotification(String adminEmail, String customerName, String customerPhone,
                                           String customerEmail, String contactInfo, String items, String services) {
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] VAS request from {} services={}", customerName, services);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(adminEmail);
            msg.setSubject("【ZilaiBuy】增值服务申请 - " + customerName);
            msg.setText(
                "收到新的增值服务申请：\n\n" +
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
                "— ZilaiBuy 系统通知"
            );
            mailSender.send(msg);
            log.info("VAS request email sent to {}", adminEmail);
        } catch (Exception e) {
            log.warn("Failed to send VAS request email ({})", e.getMessage());
        }
    }

    public void sendVasCompletionEmail(String toEmail, String displayName, String services, String itemsSummary, String adminNotes) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] VAS completion email for {} services={}", displayName, services);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】您的增值服务已完成");
            msg.setText(
                "您好，" + displayName + "！\n\n" +
                "您申请的增值服务已处理完成：\n\n" +
                "  服务项目：" + services + "\n" +
                (itemsSummary != null && !itemsSummary.isBlank() ? "  涉及包裹/订单：\n" + itemsSummary + "\n" : "") +
                (adminNotes != null && !adminNotes.isBlank() ? "\n  仓库备注：" + adminNotes + "\n" : "") +
                "\n请登录 ZilaiBuy 个人中心查看详情和照片。\n\n" +
                "— ZilaiBuy 团队"
            );
            mailSender.send(msg);
            log.info("VAS completion email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send VAS completion email to {} ({})", toEmail, e.getMessage());
        }
    }

    public void sendConfirmationEmail(String toEmail, String token) {
        String link = baseUrl + "/api/auth/confirm-email?token=" + token;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Confirmation email generated for {}", toEmail);
            log.debug("[DEV] Email confirmation link for {}: {}", toEmail, link);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】请确认您的邮箱");
            msg.setText("您好！\n\n请点击以下链接完成邮箱验证（24小时内有效）：\n\n" + link + "\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
            mailSender.send(msg);
            log.info("Confirmation email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send confirmation email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendWarehouseDispatchEmail(String toEmail, com.zilai.zilaibuy.entity.OrderEntity order) {
        if (!StringUtils.hasText(toEmail)) return;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Warehouse dispatch email for order {}", order.getOrderNo());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【紫来买】运费已付款，请安排发货 " + order.getOrderNo());

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

            msg.setText(sb.toString());
            mailSender.send(msg);
            log.info("Warehouse dispatch email sent to {} for order {}", toEmail, order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to send warehouse dispatch email for order {} ({})", order.getOrderNo(), e.getMessage());
        }
    }
}
