package com.zilai.zilaibuy.scheduler;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageReminderScheduler {

    private final ForwardingParcelRepository parcelRepository;
    private final EmailService emailService;

    /** 每天上午9点检查超60天未发货的包裹，发送仓储费提醒邮件 */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendStorageReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(60);
        List<ForwardingParcelEntity> parcels = parcelRepository.findParcelsNeedingStorageReminder(cutoff);
        log.info("[StorageReminder] Found {} parcels needing storage reminder", parcels.size());

        for (ForwardingParcelEntity parcel : parcels) {
            try {
                String userEmail = parcel.getUser().getEmail();
                if (userEmail == null || userEmail.isBlank()) {
                    parcel.setStorageFeeReminderSent(true);
                    parcelRepository.save(parcel);
                    continue;
                }
                String displayName = getDisplayName(parcel.getUser());
                long daysStored = ChronoUnit.DAYS.between(parcel.getCheckinDate(), LocalDateTime.now());
                emailService.sendStorageReminderEmail(userEmail, displayName,
                        parcel.getInboundCode(), daysStored);
                parcel.setStorageFeeReminderSent(true);
                parcelRepository.save(parcel);
            } catch (Exception e) {
                log.error("[StorageReminder] Failed for parcel id={}: {}", parcel.getId(), e.getMessage());
            }
        }
    }

    private String getDisplayName(com.zilai.zilaibuy.entity.UserEntity user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        return user.getPhone();
    }
}
