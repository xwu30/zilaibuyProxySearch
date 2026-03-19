package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.parcel.CreateParcelRequest;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ForwardingParcelService {

    private final ForwardingParcelRepository parcelRepository;
    private final UserRepository userRepository;

    @Transactional
    public ParcelDto createParcel(CreateParcelRequest req, Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        ForwardingParcelEntity parcel = new ForwardingParcelEntity();
        parcel.setUser(user);
        parcel.setInboundTrackingNo(req.inboundTrackingNo());
        parcel.setCarrier(req.carrier());
        parcel.setContent(req.content());
        parcel.setDeclaredValue(req.declaredValue());
        parcel.setProcessingOption(req.processingOption() != null ? req.processingOption() : "direct");
        parcelRepository.save(parcel);
        return ParcelDto.from(parcel);
    }

    @Transactional(readOnly = true)
    public List<ParcelDto> listUserParcels(Long userId) {
        return parcelRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(ParcelDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ParcelDto> listAllParcels(Pageable pageable) {
        return parcelRepository.findAll(pageable).map(ParcelDto::from);
    }

    @Transactional(readOnly = true)
    public Page<ParcelDto> listAllParcelsByStatus(ForwardingParcelEntity.ParcelStatus status, Pageable pageable) {
        return parcelRepository.findByStatus(status, pageable).map(ParcelDto::from);
    }

    @Transactional
    public ParcelDto updateParcel(Long parcelId, CreateParcelRequest req, Long userId) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "包裹不存在"));
        if (!parcel.getUser().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权修改此包裹");
        }
        if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.ANNOUNCED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能修改预报状态的包裹");
        }
        parcel.setInboundTrackingNo(req.inboundTrackingNo());
        parcel.setCarrier(req.carrier());
        parcel.setContent(req.content());
        parcel.setDeclaredValue(req.declaredValue());
        if (req.processingOption() != null) parcel.setProcessingOption(req.processingOption());
        parcelRepository.save(parcel);
        return ParcelDto.from(parcel);
    }

    @Transactional
    public void deleteParcel(Long parcelId, Long userId) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "包裹不存在"));
        if (!parcel.getUser().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权删除此包裹");
        }
        if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.ANNOUNCED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能删除预报状态的包裹");
        }
        parcelRepository.delete(parcel);
    }

    /** Called by warehouse checkin. Returns null if no matching parcel found. */
    @Transactional
    public OrderService.CheckinResult checkinByTrackingNo(String trackingNo, String location) {
        String no = trackingNo != null ? trackingNo.trim() : "";
        return parcelRepository.findByInboundTrackingNo(no)
                .map(parcel -> {
                    if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.ANNOUNCED) {
                        return new OrderService.CheckinResult(false,
                                "状态不符（当前: " + parcel.getStatus().name() + "）",
                                no, parcel.getStatus().name(), displayName(parcel.getUser()), null, null);
                    }
                    String loc = (location != null && !location.isBlank()) ? location.trim().toUpperCase() : "---";
                    String code = generateInboundCode(parcel.getUser(), loc);
                    parcel.setStatus(ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE);
                    parcel.setWarehouseLocation(loc);
                    parcel.setInboundCode(code);
                    parcelRepository.save(parcel);
                    return new OrderService.CheckinResult(true, "转运包裹入库成功",
                            no, "IN_WAREHOUSE", displayName(parcel.getUser()), code, parcel.getId());
                })
                .orElse(null);
    }

    private String generateInboundCode(com.zilai.zilaibuy.entity.UserEntity user, String location) {
        String cloudId = (user.getCloudId() != null && !user.getCloudId().isBlank())
                ? user.getCloudId()
                : String.format("ZL%06d", user.getId());
        long seq = parcelRepository.countByUserId(user.getId()) + 1;
        String seqPart = String.format("%03d", seq);
        return cloudId + "-" + location + "-" + seqPart;
    }

    private String displayName(com.zilai.zilaibuy.entity.UserEntity user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        return user.getPhone();
    }

    @Transactional
    public ParcelDto updateWeight(Long parcelId, Integer weightGrams) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "包裹不存在"));
        parcel.setWeight(weightGrams);
        parcelRepository.save(parcel);
        return ParcelDto.from(parcel);
    }

    @Transactional
    public ParcelDto shipParcel(Long parcelId, String outboundTrackingNo, String notes) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "包裹不存在"));
        if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能对已入库的包裹执行出库操作");
        }
        parcel.setStatus(ForwardingParcelEntity.ParcelStatus.SHIPPED);
        parcel.setOutboundTrackingNo(outboundTrackingNo);
        if (notes != null) parcel.setNotes(notes);
        parcelRepository.save(parcel);
        return ParcelDto.from(parcel);
    }

    @Transactional
    public ParcelDto deliverParcel(Long parcelId) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "包裹不存在"));
        if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.SHIPPED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能对已出库的包裹执行签收操作");
        }
        parcel.setStatus(ForwardingParcelEntity.ParcelStatus.DELIVERED);
        parcelRepository.save(parcel);
        return ParcelDto.from(parcel);
    }
}
