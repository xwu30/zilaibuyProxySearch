package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.entity.FedExShipmentEntity;
import com.zilai.zilaibuy.repository.FedExShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FedExService {

    private final FedExShipmentRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${fedex.client-id}")
    private String clientId;

    @Value("${fedex.client-secret}")
    private String clientSecret;

    @Value("${fedex.account-number}")
    private String accountNumber;

    @Value("${fedex.shipper.name:ZilaiBuy}")
    private String shipperName;

    @Value("${fedex.shipper.phone:14375551234}")
    private String shipperPhone;

    @Value("${fedex.shipper.company:ZilaiBuy}")
    private String shipperCompany;

    @Value("${fedex.shipper.street:123 Main St}")
    private String shipperStreet;

    @Value("${fedex.shipper.city:Toronto}")
    private String shipperCity;

    @Value("${fedex.shipper.state:ON}")
    private String shipperState;

    @Value("${fedex.shipper.postal:M5V3A8}")
    private String shipperPostal;

    @Value("${fedex.shipper.country:CA}")
    private String shipperCountry;

    @Value("${fedex.sandbox:true}")
    private boolean sandbox;

    private WebClient webClient;

    @jakarta.annotation.PostConstruct
    void init() {
        String base = sandbox ? "https://apis-sandbox.fedex.com" : "https://apis.fedex.com";
        webClient = WebClient.builder().baseUrl(base).build();
        log.info("FedEx service initialized in {} mode", sandbox ? "SANDBOX" : "PRODUCTION");
    }

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    private synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            String response = webClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            cachedToken = node.get("access_token").asText();
            int expiresIn = node.path("expires_in").asInt(3600);
            tokenExpiry = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (Exception e) {
            throw new RuntimeException("FedEx OAuth failed: " + e.getMessage(), e);
        }
    }

    /** One package (piece) in a possibly multi-piece shipment. Dimensions in CM. */
    public record PackageItem(
            double weightKg,
            int lengthCm,
            int widthCm,
            int heightCm
    ) {}

    public record ShipRequest(
            // shipper (overrides application.yml defaults when provided)
            String shipperName,
            String shipperPhone,
            String shipperCompany,
            String shipperStreet,
            String shipperCity,
            String shipperState,
            String shipperPostal,
            String shipperCountry,
            // recipient
            String recipientName,
            String recipientPhone,
            String recipientAddress,
            String recipientAddress2,
            String recipientCity,
            String recipientState,
            String recipientPostal,
            String recipientCountry,
            // one or more packages (multi-piece shipment)
            List<PackageItem> packages,
            String serviceType,
            // customs
            String customsDescription,
            Double customsValueAmount,
            String customsValueCurrency,
            String countryOfManufacture,
            // who pays duties & taxes: SENDER (our account) / RECIPIENT / THIRD_PARTY
            String dutiesPaymentType,
            String notes
    ) {}

    public record ShipResult(
            Long id,
            String trackingNo,
            String labelBase64,
            BigDecimal netCharge,
            String currency
    ) {}

    public record Surcharge(String name, BigDecimal amount) {}

    public record RateResult(
            String serviceType,
            String deliveryDate,
            BigDecimal baseCharge,
            java.util.List<Surcharge> surcharges,
            BigDecimal totalSurcharge,
            BigDecimal totalDiscount,
            BigDecimal netCharge,
            BigDecimal totalNetFedExCharge,
            String currency
    ) {}

    private String or(String a, String b) { return (a != null && !a.isBlank()) ? a : b; }

    // FedEx accepts a stateOrProvinceCode only for these countries; for others
    // (e.g. JP "Shiga") it must be omitted, otherwise the Ship API rejects the
    // whole request with 422 INVALID.INPUT.EXCEPTION.
    private static final java.util.Set<String> STATE_CODE_COUNTRIES =
            java.util.Set.of("US", "CA", "MX", "PR");

    /** Return a valid 2-letter state code, or null when the country doesn't use one. */
    private static String normalizeState(String state, String country) {
        if (state == null || state.isBlank()) return null;
        if (country != null && STATE_CODE_COUNTRIES.contains(country.trim().toUpperCase())) {
            String s = state.trim().toUpperCase();
            return s.length() > 2 ? s.substring(0, 2) : s;
        }
        return null;
    }

    /** FedEx phoneNumber must be digits only (no +, spaces, or hyphens). */
    private static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Build a FedEx address node, including stateOrProvinceCode only when valid. */
    private Map<String, Object> buildAddress(List<String> streetLines, String city,
                                             String state, String postal, String country) {
        Map<String, Object> addr = new HashMap<>();
        addr.put("streetLines", streetLines);
        addr.put("city", city);
        addr.put("postalCode", postal);
        addr.put("countryCode", country);
        String st = normalizeState(state, country);
        if (st != null) addr.put("stateOrProvinceCode", st);
        return addr;
    }

    private static final double KG_TO_LB = 2.20462;

    /** Result of turning the request's package list into FedEx line items + shipment totals. */
    private record PackageBuild(List<Map<String, Object>> lineItems,
                                double totalLbs, double totalKg, int count) {}

    /**
     * Build the {@code requestedPackageLineItems} array (converting each package's KG to LB)
     * along with the shipment totals needed for a multi-piece shipment (MPS).
     */
    private PackageBuild buildPackages(ShipRequest req) {
        List<PackageItem> pkgs = (req.packages() != null && !req.packages().isEmpty())
                ? req.packages() : List.of();
        if (pkgs.isEmpty()) {
            throw new RuntimeException("至少需要一个包裹");
        }
        List<Map<String, Object>> lineItems = new java.util.ArrayList<>();
        double totalLbs = 0, totalKg = 0;
        int seq = 1;
        for (PackageItem p : pkgs) {
            double lbs = p.weightKg() * KG_TO_LB;
            totalLbs += lbs;
            totalKg += p.weightKg();
            // User enters CM (metric, matching KG weight); FedEx expects inches here.
            int lin = (int) Math.round(p.lengthCm() / 2.54);
            int win = (int) Math.round(p.widthCm() / 2.54);
            int hin = (int) Math.round(p.heightCm() / 2.54);
            lineItems.add(Map.of(
                    "sequenceNumber", seq++,
                    "groupPackageCount", 1,
                    "weight", Map.of("units", "LB", "value", String.valueOf(lbs)),
                    "dimensions", Map.of(
                            "length", Math.max(lin, 1), "width", Math.max(win, 1),
                            "height", Math.max(hin, 1), "units", "IN")
            ));
        }
        return new PackageBuild(lineItems, totalLbs, totalKg, pkgs.size());
    }

    /**
     * Merge per-package label PDFs (one per piece in a multi-piece shipment) into a single
     * base64-encoded PDF so the existing single-file download flow keeps working.
     * Returns the lone label unchanged for single-package shipments.
     */
    private String mergeLabels(List<String> base64Labels) {
        if (base64Labels.isEmpty()) return null;
        if (base64Labels.size() == 1) return base64Labels.get(0);
        try {
            org.apache.pdfbox.multipdf.PDFMergerUtility merger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            merger.setDestinationStream(out);
            for (String b64 : base64Labels) {
                byte[] bytes = java.util.Base64.getMimeDecoder().decode(b64);
                merger.addSource(new java.io.ByteArrayInputStream(bytes));
            }
            merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
            return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("Failed to merge {} FedEx labels, falling back to first label", base64Labels.size(), e);
            return base64Labels.get(0);
        }
    }

    public List<RateResult> getRates(ShipRequest req) {
        String token = getToken();

        List<String> recipientLines = (req.recipientAddress2() != null && !req.recipientAddress2().isBlank())
                ? List.of(req.recipientAddress(), req.recipientAddress2())
                : List.of(req.recipientAddress());

        Map<String, Object> requestedShipment = new HashMap<>();
        requestedShipment.put("shipper", Map.of("address", buildAddress(
                List.of(or(req.shipperStreet(), shipperStreet)),
                or(req.shipperCity(), shipperCity),
                or(req.shipperState(), shipperState),
                or(req.shipperPostal(), shipperPostal),
                or(req.shipperCountry(), shipperCountry))));
        requestedShipment.put("recipient", Map.of("address", buildAddress(
                recipientLines, req.recipientCity(), req.recipientState(),
                req.recipientPostal(), req.recipientCountry())));
        requestedShipment.put("rateRequestType", List.of("LIST", "ACCOUNT"));
        requestedShipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        // Omit serviceType so FedEx returns every available service with its rate and
        // delivery date; the UI filters down to Priority / Economy and lets the user pick.
        requestedShipment.put("packagingType", "YOUR_PACKAGING");
        requestedShipment.put("shippingChargesPayment", Map.of(
                "paymentType", "SENDER",
                "payor", Map.of("responsibleParty", Map.of("accountNumber", Map.of("value", accountNumber)))
        ));
        // Rate API rejects totalWeight/totalPackageCount (INVALID.INPUT.EXCEPTION) — it
        // sums the per-package line items itself. (Ship API still needs them for MPS.)
        PackageBuild pkg = buildPackages(req);
        requestedShipment.put("requestedPackageLineItems", pkg.lineItems());

        Map<String, Object> body = Map.of(
                "accountNumber", Map.of("value", accountNumber),
                "requestedShipment", requestedShipment
        );

        try {
            String response = webClient.post()
                    .uri("/rate/v1/rates/quotes")
                    .header("Authorization", "Bearer " + token)
                    .header("X-locale", "en_US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(b -> resp.statusCode().isError()
                                    ? reactor.core.publisher.Mono.error(
                                            new RuntimeException("FedEx Rate API " + resp.statusCode().value() + ": " + b))
                                    : reactor.core.publisher.Mono.just(b)))
                    .block();

            log.info("FedEx rate raw response: {}", response);
            JsonNode root = objectMapper.readTree(response);
            JsonNode rateReplyDetails = root.path("output").path("rateReplyDetails");
            List<RateResult> results = new java.util.ArrayList<>();
            if (rateReplyDetails.isArray()) {
                for (JsonNode detail : rateReplyDetails) {
                    String svcType = detail.path("serviceType").asText("");
                    // Delivery date for "delivered by ..." display (formats vary by lane).
                    String deliveryDate = detail.path("commit").path("dateDetail").path("dayFormat").asText(
                            detail.path("operationalDetail").path("deliveryDate").asText(
                                    detail.path("commit").path("label").asText("")));
                    JsonNode ratedShipmentDetails = detail.path("ratedShipmentDetails");
                    if (ratedShipmentDetails.isArray() && ratedShipmentDetails.size() > 0) {
                        // totalNetCharge / totalNetFedExCharge live on the ratedShipmentDetails
                        // element itself; currency is nested under shipmentRateDetail.
                        // Prefer the ACCOUNT (negotiated) rate so contract discounts show;
                        // the LIST rate carries no account discount (base+surcharge == net).
                        JsonNode rsd = ratedShipmentDetails.get(0);
                        for (JsonNode cand : ratedShipmentDetails) {
                            if (cand.path("rateType").asText("").contains("ACCOUNT")) { rsd = cand; break; }
                        }
                        JsonNode srd = rsd.path("shipmentRateDetail");
                        BigDecimal netCharge = new BigDecimal(rsd.path("totalNetCharge").asText("0"));
                        BigDecimal totalNet = new BigDecimal(rsd.path("totalNetFedExCharge").asText("0"));
                        String currency = srd.path("currency").asText(rsd.path("currency").asText("CAD"));

                        BigDecimal baseCharge = new BigDecimal(rsd.path("totalBaseCharge").asText(
                                srd.path("totalBaseCharge").asText("0")));
                        // Itemized surcharges (fuel, processing fees, etc.)
                        java.util.List<Surcharge> surcharges = new java.util.ArrayList<>();
                        JsonNode surNode = srd.path("surCharges");
                        if (surNode.isArray()) {
                            for (JsonNode s : surNode) {
                                String name = s.path("description").asText(s.path("type").asText("Surcharge"));
                                BigDecimal amt = new BigDecimal(s.path("amount").asText("0"));
                                surcharges.add(new Surcharge(name, amt));
                            }
                        }
                        BigDecimal totalSurcharge = new BigDecimal(rsd.path("totalSurcharges").asText(
                                srd.path("totalSurcharges").asText("0")));
                        // Discounts: prefer explicit field, else derive base + surcharge - net.
                        BigDecimal totalDiscount = new BigDecimal(rsd.path("totalFreightDiscount").asText(
                                srd.path("totalFreightDiscounts").asText("0")));
                        if (totalDiscount.signum() == 0) {
                            BigDecimal derived = baseCharge.add(totalSurcharge).subtract(netCharge);
                            if (derived.signum() > 0) totalDiscount = derived;
                        }
                        results.add(new RateResult(svcType, deliveryDate, baseCharge, surcharges, totalSurcharge,
                                totalDiscount, netCharge, totalNet, currency));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.error("FedEx rate API error", e);
            throw new RuntimeException("FedEx 报价失败: " + e.getMessage(), e);
        }
    }

    public ShipResult createShipment(ShipRequest req, Long createdById) {
        String token = getToken();

        Map<String, Object> shipment = new HashMap<>();
        shipment.put("shipper", Map.of(
                "contact", Map.of(
                        "personName", or(req.shipperName(), shipperName),
                        "phoneNumber", normalizePhone(or(req.shipperPhone(), shipperPhone)),
                        "companyName", or(req.shipperCompany(), shipperCompany)
                ),
                "address", buildAddress(
                        List.of(or(req.shipperStreet(), shipperStreet)),
                        or(req.shipperCity(), shipperCity),
                        or(req.shipperState(), shipperState),
                        or(req.shipperPostal(), shipperPostal),
                        or(req.shipperCountry(), shipperCountry))
        ));
        List<String> recipientLines = (req.recipientAddress2() != null && !req.recipientAddress2().isBlank())
                ? List.of(req.recipientAddress(), req.recipientAddress2())
                : List.of(req.recipientAddress());
        shipment.put("recipients", List.of(Map.of(
                "contact", Map.of(
                        "personName", req.recipientName(),
                        "phoneNumber", normalizePhone(req.recipientPhone())
                ),
                "address", buildAddress(recipientLines, req.recipientCity(),
                        req.recipientState(), req.recipientPostal(), req.recipientCountry())
        )));
        shipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        shipment.put("serviceType", req.serviceType());
        shipment.put("packagingType", "YOUR_PACKAGING");
        shipment.put("shippingChargesPayment", Map.of(
                "paymentType", "SENDER",
                "payor", Map.of("responsibleParty", Map.of("accountNumber", Map.of("value", accountNumber)))
        ));
        shipment.put("labelSpecification", Map.of(
                "labelFormatType", "COMMON2D",
                "imageType", "PDF",
                "labelStockType", "PAPER_85X11_TOP_HALF_LABEL"
        ));
        PackageBuild pkg = buildPackages(req);
        shipment.put("totalPackageCount", pkg.count());
        // Do NOT send totalWeight: FedEx Ship API rejects it with a generic
        // "INVALID.INPUT.EXCEPTION: Invalid field value" (verified by replaying the
        // exact request — removing it lets the label generate). FedEx derives the
        // total from the per-package weights itself.
        shipment.put("requestedPackageLineItems", pkg.lineItems());

        if (req.customsValueAmount() != null && req.customsValueAmount() > 0) {
            String currency = or(req.customsValueCurrency(), "USD");
            String mfg = or(req.countryOfManufacture(), "JP");
            // Who pays duties & taxes. SENDER/THIRD_PARTY bill our FedEx account (payor);
            // RECIPIENT means the receiver pays on delivery, so no payor is sent.
            String dutiesType = or(req.dutiesPaymentType(), "SENDER");
            Map<String, Object> dutiesPayment = new HashMap<>();
            dutiesPayment.put("paymentType", dutiesType);
            if (!"RECIPIENT".equals(dutiesType)) {
                dutiesPayment.put("payor",
                        Map.of("responsibleParty", Map.of("accountNumber", Map.of("value", accountNumber))));
            }
            shipment.put("customsClearanceDetail", Map.of(
                    "dutiesPayment", dutiesPayment,
                    "totalCustomsValue", Map.of("amount", req.customsValueAmount(), "currency", currency),
                    "commodities", List.of(Map.of(
                            "description", or(req.customsDescription(), "Personal goods"),
                            "numberOfPieces", 1,
                            "quantity", 1,
                            "quantityUnits", "PCS",
                            "unitPrice", Map.of("amount", req.customsValueAmount(), "currency", currency),
                            "customsValue", Map.of("amount", req.customsValueAmount(), "currency", currency),
                            "weight", Map.of("units", "LB", "value", String.valueOf(pkg.totalLbs())),
                            "countryOfManufacture", mfg
                    ))
            ));
        }

        Map<String, Object> body = Map.of(
                "labelResponseOptions", "LABEL",
                "accountNumber", Map.of("value", accountNumber),
                "requestedShipment", shipment
        );

        try {
            String response = webClient.post()
                    .uri("/ship/v1/shipments")
                    .header("Authorization", "Bearer " + token)
                    .header("X-locale", "en_US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(b -> resp.statusCode().isError()
                                    ? reactor.core.publisher.Mono.error(
                                            new RuntimeException("FedEx API " + resp.statusCode().value() + ": " + b))
                                    : reactor.core.publisher.Mono.just(b)))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseShipment = root.path("output").path("transactionShipments").get(0);
            log.info("FedEx ship rating block: {}", responseShipment.path("completedShipmentDetail").path("shipmentRating"));

            String trackingNo = responseShipment.path("masterTrackingNumber").asText(null);

            // Multi-piece shipments return one label per package under pieceResponses[].
            // FedEx REST Ship API returns each label under `encodedLabel` (base64); some
            // shapes use `content`. Collect every piece's label and merge into one PDF.
            JsonNode pieceResponses = responseShipment.path("pieceResponses");
            List<String> pieceLabels = new java.util.ArrayList<>();
            if (pieceResponses.isArray()) {
                for (JsonNode pr : pieceResponses) {
                    JsonNode pkgDoc = pr.path("packageDocuments").path(0);
                    String lbl = pkgDoc.path("encodedLabel").asText(null);
                    if (lbl == null || lbl.isBlank()) lbl = pkgDoc.path("content").asText(null);
                    if (lbl != null && !lbl.isBlank()) pieceLabels.add(lbl);
                }
            }
            String labelBase64 = mergeLabels(pieceLabels);
            if (labelBase64 == null || labelBase64.isBlank()) {
                log.warn("FedEx ship OK (tracking {}) but no label found in response. pieceResponses={}",
                        trackingNo, pieceResponses.toString());
            }

            BigDecimal netCharge = null;
            String currency = "CAD";
            JsonNode rateDetails = responseShipment
                    .path("completedShipmentDetail")
                    .path("shipmentRating")
                    .path("shipmentRateDetails");
            if (rateDetails.isArray() && rateDetails.size() > 0) {
                // Prefer the ACCOUNT rate (what's actually billed); fall back to first element.
                JsonNode chosen = rateDetails.get(0);
                for (JsonNode rd : rateDetails) {
                    if ("ACCOUNT".equals(rd.path("rateType").asText())) { chosen = rd; break; }
                }
                // totalNetCharge is a flat number; currency lives under shipmentRateDetail.
                netCharge = new BigDecimal(chosen.path("totalNetCharge").asText("0"));
                currency = chosen.path("shipmentRateDetail").path("currency").asText(
                        chosen.path("currency").asText("CAD"));
            }

            FedExShipmentEntity entity = new FedExShipmentEntity();
            entity.setTrackingNo(trackingNo);
            entity.setRecipientName(req.recipientName());
            entity.setRecipientPhone(req.recipientPhone());
            entity.setRecipientAddress(
                    req.recipientAddress2() != null && !req.recipientAddress2().isBlank()
                            ? req.recipientAddress() + " " + req.recipientAddress2()
                            : req.recipientAddress());
            entity.setRecipientCity(req.recipientCity());
            entity.setRecipientState(req.recipientState());
            entity.setRecipientPostal(req.recipientPostal());
            entity.setRecipientCountry(req.recipientCountry());
            entity.setWeightLbs(pkg.totalLbs());
            entity.setWeightKg(pkg.totalKg());
            entity.setPackageCount(pkg.count());
            // Store first package's dimensions for at-a-glance reference in history.
            PackageItem first = req.packages().get(0);
            entity.setLengthIn((int) Math.round(first.lengthCm() / 2.54));
            entity.setWidthIn((int) Math.round(first.widthCm() / 2.54));
            entity.setHeightIn((int) Math.round(first.heightCm() / 2.54));
            entity.setServiceType(req.serviceType());
            entity.setNetCharge(netCharge);
            entity.setCurrency(currency);
            entity.setNotes(req.notes());
            entity.setLabelBase64(labelBase64);
            entity.setCreatedBy(createdById);
            repository.save(entity);

            return new ShipResult(entity.getId(), trackingNo, labelBase64, netCharge, currency);
        } catch (Exception e) {
            log.error("FedEx ship API error", e);
            throw new RuntimeException("FedEx 打单失败: " + e.getMessage(), e);
        }
    }

    /**
     * Void / cancel an unused FedEx label via the Ship API and mark the record CANCELLED.
     * Only the admin who created the label may void it (legacy records with no creator
     * stay cancellable by any admin).
     */
    public void cancelShipment(Long id, Long currentUserId) {
        FedExShipmentEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("运单不存在: " + id));
        if (entity.getCreatedBy() != null && !entity.getCreatedBy().equals(currentUserId)) {
            throw new RuntimeException("只能由打单人本人作废该运单");
        }
        if ("CANCELLED".equals(entity.getStatus())) return; // idempotent
        if (entity.getTrackingNo() == null || entity.getTrackingNo().isBlank()) {
            throw new RuntimeException("该运单没有运单号，无法作废");
        }

        String token = getToken();
        Map<String, Object> body = Map.of(
                "accountNumber", Map.of("value", accountNumber),
                "trackingNumber", entity.getTrackingNo(),
                "deletionControl", "DELETE_ALL_PACKAGES"
        );

        try {
            String response = webClient.put()
                    .uri("/ship/v1/shipments/cancel")
                    .header("Authorization", "Bearer " + token)
                    .header("X-locale", "en_US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(b -> resp.statusCode().isError()
                                    ? reactor.core.publisher.Mono.error(
                                            new RuntimeException("FedEx API " + resp.statusCode().value() + ": " + b))
                                    : reactor.core.publisher.Mono.just(b)))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            boolean cancelled = root.path("output").path("cancelledShipment").asBoolean(false);
            if (!cancelled) {
                log.warn("FedEx cancel did not confirm for tracking {}: {}", entity.getTrackingNo(), response);
                throw new RuntimeException("FedEx 未确认作废，可能该运单已被揽收或已作废");
            }
            entity.setStatus("CANCELLED");
            repository.save(entity);
        } catch (Exception e) {
            log.error("FedEx cancel API error", e);
            throw new RuntimeException("FedEx 作废失败: " + e.getMessage(), e);
        }
    }
}
