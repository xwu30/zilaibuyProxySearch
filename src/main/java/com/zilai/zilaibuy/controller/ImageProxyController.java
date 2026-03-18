package com.zilai.zilaibuy.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/image-proxy")
public class ImageProxyController {

    private final WebClient webClient;

    public ImageProxyController(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam String url) {
        try {
            WebClient.ResponseSpec response = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.REFERER, "https://www.amazon.co.jp/")
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .retrieve();

            byte[] body = response.bodyToMono(byte[].class).block();
            String contentType = response.toBodilessEntity().block()
                    .getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "image/jpeg");
            headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");

            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
