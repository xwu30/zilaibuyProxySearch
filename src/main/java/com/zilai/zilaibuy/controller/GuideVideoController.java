package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public read of the "新人指南" guide video URL shown at the bottom of the homepage.
 * The video is uploaded by an admin (S3) and its URL stored as the app setting
 * `guide.video.url`. Admins set it via PUT /api/admin/settings.
 */
@RestController
@RequestMapping("/api/guide-video")
@RequiredArgsConstructor
public class GuideVideoController {

    public static final String SETTING_KEY = "guide.video.url";

    private final AppSettingService appSettingService;

    @GetMapping
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(Map.of("url", appSettingService.get(SETTING_KEY, "")));
    }
}
