package com.fiapx.videoworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.ffmpeg")
public record FfmpegProperties(
    String binaryPath,
    @DefaultValue("ffprobe") String ffprobeBinaryPath,
    int fps,
    @DefaultValue("15") int processTimeoutMinutes,
    // Vídeo mais longo que isso é rejeitado antes da extração (ffprobe, rápido) — fps=1
    // decodifica o vídeo inteiro, então duração é o que mais pesa em CPU e no tamanho do
    // zip final, mais do que o tamanho do arquivo em si. <= 0 desliga o limite.
    @DefaultValue("1800") int maxDurationSeconds,
    // Prazo do próprio ffprobe (bem menor que processTimeoutMinutes — é só metadado).
    @DefaultValue("30") long probeTimeoutSeconds
) {

}
