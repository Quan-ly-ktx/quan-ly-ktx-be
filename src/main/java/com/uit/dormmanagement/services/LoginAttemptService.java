package com.uit.dormmanagement.services;

public interface LoginAttemptService {

  void recordFailedAttempt(String identifier);

  void resetAttempts(String identifier);

  boolean isLocked(String identifier);

  long getRemainingLockMinutes(String identifier);
}
