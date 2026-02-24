package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.warehouse.InboundRequest;
import com.zilai.zilaibuy.dto.warehouse.InventoryDto;
import com.zilai.zilaibuy.dto.warehouse.InventoryTransactionDto;
import com.zilai.zilaibuy.entity.InventoryEntity;
import com.zilai.zilaibuy.entity.InventoryTransactionEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.InventoryRepository;
import com.zilai.zilaibuy.repository.InventoryTransactionRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public InventoryDto inbound(InboundRequest req, Long operatorId) {
        UserEntity operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        InventoryEntity inventory;
        if (req.inventoryId() != null) {
            inventory = inventoryRepository.findById(req.inventoryId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "库存记录不存在"));
        } else if (StringUtils.hasText(req.sku())) {
            inventory = inventoryRepository.findBySku(req.sku()).orElseGet(() -> {
                InventoryEntity newInv = new InventoryEntity();
                newInv.setItemName(req.itemName() != null ? req.itemName() : req.sku());
                newInv.setSku(req.sku());
                return newInv;
            });
        } else {
            InventoryEntity newInv = new InventoryEntity();
            newInv.setItemName(req.itemName());
            inventory = newInv;
        }

        inventory.setQuantity(inventory.getQuantity() + req.quantityDelta());
        inventoryRepository.save(inventory);

        InventoryTransactionEntity tx = new InventoryTransactionEntity();
        tx.setInventory(inventory);
        tx.setType(InventoryTransactionEntity.TransactionType.INBOUND);
        tx.setQuantityDelta(req.quantityDelta());
        tx.setQuantityAfter(inventory.getQuantity());
        tx.setOperator(operator);
        tx.setNotes(req.notes());
        transactionRepository.save(tx);

        return InventoryDto.from(inventory);
    }

    @Transactional(readOnly = true)
    public Page<InventoryDto> listInventory(Pageable pageable) {
        return inventoryRepository.findAll(pageable).map(InventoryDto::from);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionDto> listTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable).map(InventoryTransactionDto::from);
    }
}
