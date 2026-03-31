package com.zilai.zilaibuy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/upload")
@PreAuthorize("hasRole('ADMIN')")
public class UploadController {

    @Value("${app.s3.bucket:zilaibuy-media}")
    private String bucket;

    @Value("${app.s3.region:us-east-1}")
    private String region;

    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> presign(@RequestBody Map<String, String> body) {
        String contentType = body.getOrDefault("contentType", "image/jpeg");
        String ext = contentType.contains("/") ? "." + contentType.split("/")[1].replace("jpeg", "jpg") : ".jpg";
        String key = "packing-photos/" + UUID.randomUUID() + ext;

        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(contentType)
                                .build())
                        .build());

        presigner.close();

        String uploadUrl = presigned.url().toString();
        String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        return ResponseEntity.ok(Map.of(
                "uploadUrl", uploadUrl,
                "publicUrl", publicUrl
        ));
    }
}
