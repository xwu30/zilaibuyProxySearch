package com.zilai.zilaibuy.dto.admin;

import com.zilai.zilaibuy.entity.UserEntity;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull UserEntity.Role role) {}
