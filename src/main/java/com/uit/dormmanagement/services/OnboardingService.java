package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;

public interface OnboardingService {
  OnboardingResponse onboardTenant(OnboardingRequest request);
}
