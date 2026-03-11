package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.ShippingRatesEntity;
import com.zilai.zilaibuy.repository.ShippingRatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingRatesService {

    private final ShippingRatesRepository repo;

    /** 获取运费 JSON；若数据库中尚无记录则返回 null（前端使用内置默认值）。 */
    @Transactional(readOnly = true)
    public String getRatesJson() {
        return repo.findById(1L).map(ShippingRatesEntity::getRatesJson).orElse(null);
    }

    /** 保存或覆盖运费 JSON（幂等：始终 upsert id=1 的行）。 */
    @Transactional
    public void saveRatesJson(String json, String updatedBy) {
        ShippingRatesEntity entity = repo.findById(1L).orElseGet(() -> {
            ShippingRatesEntity e = new ShippingRatesEntity();
            e.setId(1L);
            return e;
        });
        entity.setRatesJson(json);
        entity.setUpdatedBy(updatedBy);
        repo.save(entity);
    }
}
