package com.fiapx.videoapi.domain.port;

public interface LoginRateLimiter {

  boolean isBlocked(String email);

  void registerFailedAttempt(String email);

  void reset(String email);
}
