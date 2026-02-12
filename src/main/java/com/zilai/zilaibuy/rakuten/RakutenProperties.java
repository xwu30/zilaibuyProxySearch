package com.zilai.zilaibuy.rakuten;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rakuten")
public class RakutenProperties {

    private String baseUrl;
    private String path;

    private String applicationId;
    private String affiliateId;
    private String accessKey;

    private String referer;
    private Integer timeoutMs = 8000;

    // ✅ 给默认值，避免每次都传
    private Integer defaultHits = 5;
    private Integer defaultImageFlag = 1;

    public String getReferer() { return referer; }
    public void setReferer(String referer) { this.referer = referer; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public Integer getDefaultHits() { return defaultHits; }
    public void setDefaultHits(Integer defaultHits) { this.defaultHits = defaultHits; }

    public Integer getDefaultImageFlag() { return defaultImageFlag; }
    public void setDefaultImageFlag(Integer defaultImageFlag) { this.defaultImageFlag = defaultImageFlag; }
}
