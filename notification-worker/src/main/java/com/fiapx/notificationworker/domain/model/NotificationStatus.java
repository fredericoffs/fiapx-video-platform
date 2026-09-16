package com.fiapx.notificationworker.domain.model;

public enum NotificationStatus {
  /** Reivindicação em andamento: reserva a linha (índice único) antes do canal ser chamado. */
  SENDING,
  SENT,
  FAILED
}
