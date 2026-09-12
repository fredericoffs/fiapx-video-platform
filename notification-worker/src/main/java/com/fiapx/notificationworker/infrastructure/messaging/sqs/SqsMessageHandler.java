package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import java.util.Map;

@FunctionalInterface
public interface SqsMessageHandler {

  String CORRELATION_ID = "correlationId";
  String EVENT_ID = "eventId";

  void handle(String body, Map<String, String> attributes);
}
