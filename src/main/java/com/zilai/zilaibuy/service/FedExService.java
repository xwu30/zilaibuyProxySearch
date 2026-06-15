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
            double weightLbs,
            int lengthIn,
            int widthIn,
            int heightIn,
            String serviceType,
            // customs
            String customsDescription,
            Double customsValueAmount,
            String customsValueCurrency,
            String countryOfManufacture,
            String notes
    ) {}

    public record ShipResult(
            Long id,
            String trackingNo,
            String labelBase64,
            BigDecimal netCharge,
            String currency
    ) {}

    public record RateResult(
            String serviceType,
            BigDecimal netCharge,
            BigDecimal totalNetFedExCharge,
            String currency
    ) {}

    private String or(String a, String b) { return (a != null && !a.isBlank()) ? a : b; }

    public List<RateResult> getRates(ShipRequest req) {
        String token = getToken();

        List<String> recipientLines = (req.recipientAddress2() != null && !req.recipientAddress2().isBlank())
                ? List.of(req.recipientAddress(), req.recipientAddress2())
                : List.of(req.recipientAddress());

        String sState = or(req.shipperState(), shipperState);
        if (sState.length() > 2) sState = sState.substring(0, 2).toUpperCase();

        Map<String, Object> requestedShipment = new HashMap<>();
        requestedShipment.put("shipper", Map.of(
                "address", Map.of(
                        "streetLines", List.of(or(req.shipperStreet(), shipperStreet)),
                        "city", or(req.shipperCity(), shipperCity),
                        "stateOrProvinceCode", sState,
                        "postalCode", or(req.shipperPostal(), shipperPostal),
                        "countryCode", or(req.shipperCountry(), shipperCountry)
                )
        ));
        requestedShipment.put("recipient", Map.of(
                "address", Map.of(
                        "streetLines", recipientLines,
                        "city", req.recipientCity(),
                        "stateOrProvinceCode", req.recipientState(),
                        "postalCode", req.recipientPostal(),
                        "countryCode", req.recipientCountry()
                )
        ));
        requestedShipment.put("rateRequestType", List.of("LIST", "ACCOUNT"));
        requestedShipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        if (req.serviceType() != null && !req.serviceType().isBlank()) {
            requestedShipment.put("serviceType", req.serviceType());
        }
        requestedShipment.put("packagingType", "YOUR_PACKAGING");
        requestedShipment.put("shippingChargesPayment", Map.of(
                "paymentType", "SENDER",
                "payor", Map.of("responsibleParty", Map.of("accountNumber", Map.of("value", accountNumber)))
        ));
        requestedShipment.put("requestedPackageLineItems", List.of(Map.of(
                "weight", Map.of("units", "LB", "value", String.valueOf(req.weightLbs())),
                "dimensions", Map.of(
                        "length", req.lengthIn(), "width", req.widthIn(),
                        "height", req.heightIn(), "units", "IN")
        )));

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
                    JsonNode ratedShipmentDetails = detail.path("ratedShipmentDetails");
                    if (ratedShipmentDetails.isArray() && ratedShipmentDetails.size() > 0) {
                        // totalNetCharge / totalNetFedExCharge live on the ratedShipmentDetails
                        // element itself; currency is nested under shipmentRateDetail.
                        JsonNode rsd = ratedShipmentDetails.get(0);
                        JsonNode shipmentRateDetail = rsd.path("shipmentRateDetail");
                        BigDecimal netCharge = new BigDecimal(rsd.path("totalNetCharge").asText("0"));
                        BigDecimal totalNet = new BigDecimal(rsd.path("totalNetFedExCharge").asText("0"));
                        String currency = shipmentRateDetail.path("currency").asText(
                                rsd.path("currency").asText("CAD"));
                        results.add(new RateResult(svcType, netCharge, totalNet, currency));
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
                        "phoneNumber", or(req.shipperPhone(), shipperPhone),
                        "companyName", or(req.shipperCompany(), shipperCompany)
                ),
                "address", Map.of(
                        "streetLines", List.of(or(req.shipperStreet(), shipperStreet)),
                        "city", or(req.shipperCity(), shipperCity),
                        "stateOrProvinceCode", or(req.shipperState(), shipperState),
                        "postalCode", or(req.shipperPostal(), shipperPostal),
                        "countryCode", or(req.shipperCountry(), shipperCountry)
                )
        ));
        List<String> recipientLines = (req.recipientAddress2() != null && !req.recipientAddress2().isBlank())
                ? List.of(req.recipientAddress(), req.recipientAddress2())
                : List.of(req.recipientAddress());
        shipment.put("recipients", List.of(Map.of(
                "contact", Map.of(
                        "personName", req.recipientName(),
                        "phoneNumber", req.recipientPhone()
                ),
                "address", Map.of(
                        "streetLines", recipientLines,
                        "city", req.recipientCity(),
                        "stateOrProvinceCode", req.recipientState(),
                        "postalCode", req.recipientPostal(),
                        "countryCode", req.recipientCountry()
                )
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
        shipment.put("requestedPackageLineItems", List.of(Map.of(
                "weight", Map.of("units", "LB", "value", String.valueOf(req.weightLbs())),
                "dimensions", Map.of(
                        "length", req.lengthIn(), "width", req.widthIn(),
                        "height", req.heightIn(), "units", "IN")
        )));

        if (req.customsValueAmount() != null && req.customsValueAmount() > 0) {
            String currency = or(req.customsValueCurrency(), "CAD");
            String mfg = or(req.countryOfManufacture(), "JP");
            shipment.put("customsClearanceDetail", Map.of(
                    "dutiesPayment", Map.of(
                            "paymentType", "SENDER",
                            "payor", Map.of("responsibleParty", Map.of("accountNumber", Map.of("value", accountNumber)))
                    ),
                    "totalCustomsValue", Map.of("amount", req.customsValueAmount(), "currency", currency),
                    "commodities", List.of(Map.of(
                            "description", or(req.customsDescription(), "Personal goods"),
                            "quantity", 1,
                            "quantityUnits", "PCS",
                            "unitPrice", Map.of("amount", req.customsValueAmount(), "currency", currency),
                            "customsValue", Map.of("amount", req.customsValueAmount(), "currency", currency),
                            "weight", Map.of("units", "LB", "value", String.valueOf(req.weightLbs())),
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

            String trackingNo = responseShipment.path("masterTrackingNumber").asText(null);

            // FedEx REST Ship API returns the label under `encodedLabel` (base64);
            // some shapes use `content`. Use path(index) (not get) to avoid NPE on empty arrays.
            JsonNode pkgDoc = responseShipment.path("pieceResponses").path(0)
                    .path("packageDocuments").path(0);
            String labelBase64 = pkgDoc.path("encodedLabel").asText(null);
            if (labelBase64 == null || labelBase64.isBlank()) {
                labelBase64 = pkgDoc.path("content").asText(null);
            }
            if (labelBase64 == null || labelBase64.isBlank()) {
                log.warn("FedEx ship OK (tracking {}) but no label found in response. packageDocuments={}",
                        trackingNo, pkgDoc.toString());
            }

            BigDecimal netCharge = null;
            String currency = "CAD";
            JsonNode rateDetails = responseShipment
                    .path("completedShipmentDetail")
                    .path("shipmentRating")
                    .path("shipmentRateDetails");
            if (rateDetails.isArray() && rateDetails.size() > 0) {
                JsonNode charge = rateDetails.get(0).path("totalNetCharge");
                netCharge = new BigDecimal(charge.path("amount").asText("0"));
                currency = charge.path("currency").asText("CAD");
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
            entity.setWeightLbs(req.weightLbs());
            entity.setLengthIn(req.lengthIn());
            entity.setWidthIn(req.widthIn());
            entity.setHeightIn(req.heightIn());
            entity.setServiceType(req.serviceType());
            entity.setNetCharge(netCharge);
            entity.setCurrency(currency);
            entity.setNotes(req.notes());
            entity.setCreatedBy(createdById);
            repository.save(entity);

            return new ShipResult(entity.getId(), trackingNo, labelBase64, netCharge, currency);
        } catch (Exception e) {
            log.error("FedEx ship API error", e);
            throw new RuntimeException("FedEx 打单失败: " + e.getMessage(), e);
        }
    }

    /** Void / cancel an unused FedEx label via the Ship API and mark the record CANCELLED. */
    public void cancelShipment(Long id) {
        FedExShipmentEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("运单不存在: " + id));
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
