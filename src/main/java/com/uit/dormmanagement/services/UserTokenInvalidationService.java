package com.uit.dormmanagement.services;

import java.util.UUID;

public interface UserTokenInvalidationService {

  String getOrCreateGeneration(UUID userId);

  boolean isCurrentGeneration(UUID userId, String generation);

  void invalidateAll(UUID userId);
}
