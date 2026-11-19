package com.uit.dormmanagement.services;

import java.util.Collection;
import java.util.UUID;

public interface RoomHistoryGuard {
  boolean hasProtectedHistory(UUID roomId, Collection<UUID> bedIds);
}
