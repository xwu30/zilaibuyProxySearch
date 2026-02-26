package com.zilai.zilaibuy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    @Value("${google.places.api-key}")
    private String placesApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * GET /api/address/autocomplete?input=...
     * Proxies Google Places Autocomplete API, restricted to Canada.
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<?> autocomplete(@RequestParam String input) {
        if (input == null || input.trim().length() < 2) {
            return ResponseEntity.ok(Map.of("predictions", List.of(), "status", "ZERO_RESULTS"));
        }
        String url = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/place/autocomplete/json")
                .queryParam("input", input.trim())
                .queryParam("components", "country:ca")
                .queryParam("types", "address")
                .queryParam("language", "en")
                .queryParam("key", placesApiKey)
                .build()
                .toUriString();
        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/address/place/{placeId}
     * Proxies Google Places Details API to resolve address components.
     */
    @GetMapping("/place/{placeId}")
    public ResponseEntity<?> getPlaceDetails(@PathVariable String placeId) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/place/details/json")
                .queryParam("place_id", placeId)
                .queryParam("fields", "address_components")
                .queryParam("key", placesApiKey)
                .build()
                .toUriString();
        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        return ResponseEntity.ok(response);
    }
}
