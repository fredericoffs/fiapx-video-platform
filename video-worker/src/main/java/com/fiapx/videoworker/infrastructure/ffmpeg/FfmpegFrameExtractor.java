package com.fiapx.videoworker.infrastructure.ffmpeg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.domain.port.FrameExtractor;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;

@Component
public class FfmpegFrameExtractor implements FrameExtractor {

	private final FfmpegProperties ffmpegProperties;

	public FfmpegFrameExtractor(FfmpegProperties ffmpegProperties) {
		this.ffmpegProperties = ffmpegProperties;
	}

	@Override
	public void extractFrames(Path videoFile, Path outputDir, int fps) {
		List<String> command = List.of(ffmpegProperties.binaryPath(), "-y", "-hide_banner", "-loglevel", "error", "-i",
				videoFile.toString(), "-vf", "fps=" + fps, outputDir.resolve("frame_%04d.png").toString());

		Process process;
		String output;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
			output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			boolean finished = process.waitFor(ffmpegProperties.processTimeoutMinutes(), TimeUnit.MINUTES);
			if (!finished) {
				process.destroyForcibly();
				throw new FfmpegProcessingException("Timeout de processamento excedido (" + videoFile + ")");
			}
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao executar ffmpeg para " + videoFile, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FfmpegProcessingException("Execução do ffmpeg interrompida para " + videoFile, e);
		}

		if (process.exitValue() != 0) {
			throw new FfmpegProcessingException("ffmpeg saiu com código " + process.exitValue() + ": " + output);
		}

		try (Stream<Path> files = Files.list(outputDir)) {
			if (files.findAny().isEmpty()) {
				throw new FfmpegProcessingException("Nenhum frame extraído — vídeo pode estar corrompido: " + videoFile);
			}
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao ler diretório de frames " + outputDir, e);
		}
	}
}
