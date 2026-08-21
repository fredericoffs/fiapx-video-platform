package com.fiapx.notificationworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiapx.notification")
public record NotificationProperties(
    String fromAddress
) {

}
