package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.VasRequestRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private final OrderRepository orderRepository;
    private final VasRequestRepository vasRequestRepository;

    private static final Map<String, Long> VAS_FEE_JPY = Map.of(
            "item_inspect", 4300L,
            "photo",        6400L,
            "special_pack", 6400L
    );
    private static final BigDecimal JPY_TO_CNY = new BigDecimal("0.0467");

    public record TxItem(
            String title,
            Integer priceJpy,
            BigDecimal priceCny,
            String platform,
            String imageUrl,
            String trackingNo
    ) {}

    public record TransactionDto(
            String id,
            String type,
            String orderNo,
            String description,
            BigDecimal amountCny,
            Integer amountJpy,
            LocalDateTime paidAt,
            String status,
            List<TxItem> items,        // order items for PROXY
            String shippingLine,       // for SHIPPING
            String packingNo,
            String transitTrackingNo
    ) {}

    @GetMapping("/history")
    @Transactional
    public ResponseEntity<List<TransactionDto>> getHistory(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        List<TransactionDto> list = new ArrayList<>();

        // 1. Paid orders
        var orders = orderRepository.findByUserId(
                principal.id(),
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        for (OrderEntity o : orders) {
            if (o.getStatus() == OrderEntity.OrderStatus.PENDING_PAYMENT
                    || o.getStatus() == OrderEntity.OrderStatus.CANCELLED) continue;

            List<TxItem> txItems = o.getItems().stream()
                    .map(i -> new TxItem(
                            i.getProductTitle(),
                            i.getPriceJpy(),
                            i.getPriceCny(),
                            i.getPlatform(),
                            i.getImageUrl(),
                            i.getItemTrackingNo()
                    ))
                    .toList();

            String itemSummary = txItems.stream()
                    .limit(2)
                    .map(i -> i.title() != null
                            ? i.title().substring(0, Math.min(20, i.title().length()))
                            : "")
                    .collect(Collectors.joining("、"));
            if (txItems.size() > 2) itemSummary += " 等" + txItems.size() + "件";

            int proxyJpy = txItems.stream()
                    .mapToInt(i -> i.priceJpy() != null ? i.priceJpy() : 0).sum();

            list.add(new TransactionDto(
                    "ORDER-" + o.getId(),
                    "PROXY",
                    o.getOrderNo(),
                    "代购订单：" + itemSummary,
                    o.getTotalCny(),
                    proxyJpy > 0 ? proxyJpy : null,
                    o.getUpdatedAt(),
                    statusLabel(o.getStatus()),
                    txItems,
                    null,
                    o.getPackingNo(),
                    o.getTransitTrackingNo()
            ));

            // Shipping payment
            if (o.getShippingFeeCny() != null && isShippingPaid(o.getStatus())) {
                int shipJpy = (int) Math.round(
                        o.getShippingFeeCny().doubleValue() / JPY_TO_CNY.doubleValue());
                list.add(new TransactionDto(
                        "SHIP-" + o.getId(),
                        "SHIPPING",
                        o.getOrderNo(),
                        "集运运费：" + (o.getRequestedShippingLineName() != null
                                ? o.getRequestedShippingLineName() : o.getOrderNo()),
                        o.getShippingFeeCny(),
                        shipJpy,
                        o.getUpdatedAt(),
                        "已支付",
                        null,
                        o.getRequestedShippingLineName(),
                        o.getPackingNo(),
                        o.getTransitTrackingNo()
                ));
            }
        }

        // 2. VAS payments
        vasRequestRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .filter(v -> v.getStatus() == VasRequestEntity.VasStatus.PAID
                          || v.getStatus() == VasRequestEntity.VasStatus.DONE)
                .forEach(v -> {
                    long totalJpy = Arrays.stream(v.getServices().split(","))
                            .mapToLong(s -> VAS_FEE_JPY.getOrDefault(s.trim(), 0L))
                            .sum();
                    BigDecimal amtCny = BigDecimal.valueOf(totalJpy).multiply(JPY_TO_CNY)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    String svcLabel = Arrays.stream(v.getServices().split(","))
                            .map(String::trim).map(TransactionHistoryController::vasLabel)
                            .collect(Collectors.joining("、"));
                    String summary = v.getItemsSummary() != null
                            ? v.getItemsSummary().substring(0, Math.min(40, v.getItemsSummary().length()))
                            : "";
                    list.add(new TransactionDto(
                            "VAS-" + v.getId(),
                            "VAS",
                            null,
                            "增值服务：" + svcLabel + (summary.isEmpty() ? "" : "（" + summary + "）"),
                            amtCny,
                            (int) totalJpy,
                            v.getUpdatedAt(),
                            "已支付",
                            null,
                            svcLabel,
                            null,
                            null
                    ));
                });

        list.sort(Comparator.comparing(TransactionDto::paidAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return ResponseEntity.ok(list);
    }

    private static boolean isShippingPaid(OrderEntity.OrderStatus s) {
        return s == OrderEntity.OrderStatus.PACKING
                || s == OrderEntity.OrderStatus.SHIPPED
                || s == OrderEntity.OrderStatus.DELIVERED;
    }

    private static String statusLabel(OrderEntity.OrderStatus s) {
        return switch (s) {
            case PURCHASING       -> "采购中";
            case IN_TRANSIT       -> "运往仓库";
            case IN_WAREHOUSE     -> "已入库";
            case FEE_QUOTED       -> "运费已报价";
            case AWAITING_PAYMENT -> "待支付运费";
            case PACKING          -> "打包中";
            case SHIPPED          -> "已发货";
            case DELIVERED        -> "已签收";
            case CANCELLED        -> "已取消";
            default               -> s.name();
        };
    }

    private static String vasLabel(String code) {
        return switch (code) {
            case "item_inspect" -> "验货";
            case "photo"        -> "拍照";
            case "special_pack" -> "特殊包装";
            default             -> code;
        };
    }
}
