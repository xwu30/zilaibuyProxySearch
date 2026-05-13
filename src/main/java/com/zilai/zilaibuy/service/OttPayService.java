package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * OTT Pay payment gateway client.
 * Handles AES-ECB encryption, H5PAY (WeChat Pay online) order creation,
 * STATUS_QUERY, and callback decryption.
 *
 * Encryption scheme:
 *   md5     = MD5(sorted_values_concatenated).toUpperCase()
 *   aesKey  = MD5(md5 + signKey).toUpperCase().substring(8, 24)
 *   data    = Base64(AES-ECB-PKCS5(innerJson, aesKey))
 */
@Slf4j
@Service
public class OttPayService {

    private static final String ENDPOINT = "https://frontapi.ottpay.com/processV2";

    @Value("${ottPay.merchant-id:ON00011082}")
    private String merchantId;

    @Value("${ottPay.sign-key:E3BA1A6E1E18C8C7}")
    private String signKey;

    @Value("${ottPay.operator-id:0000040308}")
    private String operatorId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Crypto helpers ────────────────────────────────────────────────────────

    private String md5Upper(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 failed", e);
        }
    }

    /** Derive 16-char AES key from the data md5 checksum + signKey */
    private String buildAesKey(String dataMd5) {
        return md5Upper(dataMd5 + signKey).substring(8, 24);
    }

    /** Compute MD5 checksum of a data map: sort keys, concatenate values, MD5. */
    private String computeDataMd5(Map<String, Object> data) {
        TreeMap<String, Object> sorted = new TreeMap<>(data);
        StringBuilder sb = new StringBuilder();
        for (Object v : sorted.values()) {
            if (v != null) sb.append(v);
        }
        return md5Upper(sb.toString());
    }

    private String aesEncrypt(String plainText, String aesKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    private String aesDecrypt(String encryptedBase64, String aesKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(encryptedBase64)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> postToOttPay(String action, String version,
                                              Map<String, Object> innerData) {
        try {
            String innerJson = objectMapper.writeValueAsString(innerData);
            String dataMd5 = computeDataMd5(innerData);
            String aesKey = buildAesKey(dataMd5);
            String encryptedData = aesEncrypt(innerJson, aesKey);

            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("action", action);
            outer.put("version", version);
            outer.put("merchant_id", merchantId);
            outer.put("data", encryptedData);
            outer.put("md5", dataMd5);

            byte[] requestBytes = objectMapper.writeValueAsBytes(outer);

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
            }

            int httpStatus = conn.getResponseCode();
            InputStream is = httpStatus >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] responseBytes = is != null ? is.readAllBytes() : new byte[0];
            if (is != null) is.close();

            Map<String, Object> outerResponse = objectMapper.readValue(
                    responseBytes,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            log.info("[OttPay] {} rsp_code={}", action, outerResponse.get("rsp_code"));

            String responseDataStr = (String) outerResponse.get("data");
            String responseMd5 = (String) outerResponse.get("md5");

            if (responseDataStr != null && responseMd5 != null) {
                String responseAesKey = buildAesKey(responseMd5);
                String decryptedJson = aesDecrypt(responseDataStr, responseAesKey);
                Map<String, Object> inner = objectMapper.readValue(
                        decryptedJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                inner.put("_rsp_code", outerResponse.get("rsp_code"));
                inner.put("_rsp_msg", outerResponse.get("rsp_msg"));
                return inner;
            }

            return outerResponse;

        } catch (Exception e) {
            log.error("[OttPay] {} request error: {}", action, e.getMessage(), e);
            return Map.of("_error", e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create WeChat Pay QR code order (ACTIVEPAY).
     * Works from any browser — user scans the returned QR code with WeChat.
     *
     * @param amountJpy   amount in JPY (whole yen, no conversion)
     * @param ottOrderRef unique order reference we assign (used to query later)
     * @param callbackUrl async callback URL for OTT Pay to POST to
     * @return QR code content string — either a direct image URL or a weixin:// URI
     *         that the frontend should render as a scannable QR code image
     */
    public String createQrPayOrder(long amountJpy, String ottOrderRef, String callbackUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", String.valueOf(amountJpy));
        data.put("biz_type", "WECHATPAY");
        data.put("call_back_url", callbackUrl);
        data.put("currency_type", "JPY");
        data.put("operator_id", operatorId);
        data.put("order_id", ottOrderRef);

        Map<String, Object> response = postToOttPay("ACTIVEPAY", "1.0", data);
        log.info("[OttPay] QRPAY ottOrderRef={} response={}", ottOrderRef, response);

        for (String key : List.of("qrcode_url", "code_url", "qr_code", "qrCode", "payInfo")) {
            Object val = response.get(key);
            if (val != null && !val.toString().isBlank()) return val.toString();
        }

        throw new RuntimeException("OTT Pay QRPAY did not return a QR code. Response: " + response);
    }

    /**
     * Query the status of an order by the ref we sent to OTT Pay.
     */
    public Map<String, Object> queryOrder(String ottOrderRef) {
        Map<String, Object> data = Map.of("order_id", ottOrderRef);
        return postToOttPay("STATUS_QUERY", "1.0", data);
    }

    /**
     * Returns true if the query/callback result indicates the payment is complete.
     */
    public boolean isPaymentCompleted(Map<String, Object> result) {
        String status = String.valueOf(result.getOrDefault("order_status", ""));
        return "authorised".equalsIgnoreCase(status)
                || "captured".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status)
                || "PAID".equalsIgnoreCase(status);
    }

    /**
     * Decrypt and return the inner data map from an async callback POST body.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decryptCallback(Map<String, Object> payload) {
        try {
            String encryptedData = (String) payload.get("data");
            String md5 = (String) payload.get("md5");
            if (encryptedData == null || md5 == null)
                throw new IllegalArgumentException("Missing data or md5 in OTT Pay callback");
            String aesKey = buildAesKey(md5);
            String decrypted = aesDecrypt(encryptedData, aesKey);
            return objectMapper.readValue(
                    decrypted,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            log.error("[OttPay] Callback decrypt failed: {}", e.getMessage(), e);
            throw new RuntimeException("OTT Pay callback decrypt failed", e);
        }
    }
}
