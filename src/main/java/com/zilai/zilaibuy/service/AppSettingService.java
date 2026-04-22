package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.AppSettingEntity;
import com.zilai.zilaibuy.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository repo;

    public String get(String key, String defaultValue) {
        return repo.findById(key).map(AppSettingEntity::getValue).orElse(defaultValue);
    }

    public void set(String key, String value) {
        repo.save(new AppSettingEntity(key, value));
    }

    public Map<String, String> getAll() {
        return repo.findAll().stream()
                .collect(Collectors.toMap(AppSettingEntity::getKey, AppSettingEntity::getValue));
    }
}
