package com.fiapx.videoworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.ffmpeg")
public record FfmpegProperties(
    String binaryPath,
    int fps,
    @DefaultValue("15") int processTimeoutMinutes
) {

}
