package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.service.GoogleTranslateService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/translate")
public class TranslateController {

    private final GoogleTranslateService translateService;

    public TranslateController(GoogleTranslateService translateService) {
        this.translateService = translateService;
    }

    /**
     * POST /api/translate
     * Body: { "text": "...", "source": "ja", "target": "zh-CN" }
     * source/target 可省略，默认 ja -> zh-CN
     */
    @PostMapping
    public Map<String, String> translate(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String source = body.getOrDefault("source", "ja");
        String target = body.getOrDefault("target", "zh-CN");
        String translated = translateService.translate(text, source, target);
        return Map.of("translatedText", translated);
    }
}