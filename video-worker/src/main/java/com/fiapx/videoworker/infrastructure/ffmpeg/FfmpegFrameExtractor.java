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

/**
 * Executa o ffmpeg como subprocesso com prazo real: a saída vai para um arquivo (não leio o
 * stdout de forma bloqueante antes do {@code waitFor}, senão o timeout nunca dispara), e ao
 * expirar ou ser interrompido eu derrubo o processo e seus descendentes antes de falhar.
 */
@Component
public class FfmpegFrameExtractor implements FrameExtractor {

	static final int MAX_LOG_BYTES = 64 * 1024;
	private static final long KILL_GRACE_SECONDS = 5;
	private static final long PROBE_TIMEOUT_SECONDS = 30;

	private final FfmpegProperties ffmpegProperties;

	public FfmpegFrameExtractor(FfmpegProperties ffmpegProperties) {
		this.ffmpegProperties = ffmpegProperties;
	}

	@Override
	public void extractFrames(Path videoFile, Path outputDir, int fps) {
		ensureDurationWithinLimit(videoFile);

		List<String> command = List.of(ffmpegProperties.binaryPath(), "-y", "-hide_banner", "-loglevel", "error", "-i",
				videoFile.toString(), "-vf", "fps=" + fps, outputDir.resolve("frame_%04d.png").toString());

		Path logFile = createLogFile(outputDir);
		try {
			int exitCode = run(command, logFile, videoFile);
			if (exitCode != 0) {
				throw new FfmpegProcessingException("ffmpeg saiu com código " + exitCode + ": " + readLog(logFile));
			}
			ensureFramesWereProduced(outputDir, videoFile);
		} finally {
			try {
				Files.deleteIfExists(logFile);
			} catch (IOException ignored) {
				// log é temporário; falha na limpeza não muda o resultado
			}
		}
	}

	private int run(List<String> command, Path logFile, Path videoFile) {
		Process process;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao executar ffmpeg para " + videoFile, e);
		}

		try {
			boolean finished = process.waitFor(ffmpegProperties.processTimeoutMinutes(), TimeUnit.MINUTES);
			if (!finished) {
				killTree(process);
				throw new FfmpegProcessingException("Timeout de processamento excedido após "
						+ ffmpegProperties.processTimeoutMinutes() + " min (" + videoFile + ")");
			}
			return process.exitValue();
		} catch (InterruptedException e) {
			killTree(process);
			Thread.currentThread().interrupt();
			throw new FfmpegProcessingException("Execução do ffmpeg interrompida para " + videoFile, e);
		}
	}

	/**
	 * fps=1 decodifica o vídeo inteiro — duração é o que mais pesa em CPU e no tamanho do zip
	 * final, então consulto com ffprobe (rápido, não decodifica frame nenhum) antes de pagar o
	 * custo da extração completa.
	 */
	private void ensureDurationWithinLimit(Path videoFile) {
		int maxSeconds = ffmpegProperties.maxDurationSeconds();
		if (maxSeconds <= 0) {
			return;
		}
		double durationSeconds = probeDurationSeconds(videoFile);
		if (durationSeconds > maxSeconds) {
			throw new FfmpegProcessingException(String.format(
					"Vídeo com %.0fs de duração excede o limite de %ds permitido nesta infraestrutura", durationSeconds,
					maxSeconds));
		}
	}

	private double probeDurationSeconds(Path videoFile) {
		List<String> command = List.of(ffmpegProperties.ffprobeBinaryPath(), "-v", "error", "-show_entries",
				"format=duration", "-of", "default=noprint_wrappers=1:nokey=1", videoFile.toString());

		Process process;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao executar ffprobe para " + videoFile, e);
		}

		String output;
		try {
			// Saída do ffprobe é minúscula (um número) — ler tudo antes do waitFor não arrisca o
			// deadlock que o run() do ffmpeg evita (ali a saída pode ser grande e demorada).
			output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				killTree(process);
				throw new FfmpegProcessingException("Timeout ao consultar duração do vídeo " + videoFile);
			}
		} catch (IOException e) {
			killTree(process);
			throw new FfmpegProcessingException("Falha ao ler saída do ffprobe para " + videoFile, e);
		} catch (InterruptedException e) {
			killTree(process);
			Thread.currentThread().interrupt();
			throw new FfmpegProcessingException("Execução do ffprobe interrompida para " + videoFile, e);
		}

		if (process.exitValue() != 0) {
			throw new FfmpegProcessingException(
					"ffprobe saiu com código " + process.exitValue() + " para " + videoFile + ": " + output);
		}
		try {
			return Double.parseDouble(output);
		} catch (NumberFormatException e) {
			throw new FfmpegProcessingException("Não foi possível determinar a duração de " + videoFile + " (saída: \""
					+ output + "\")");
		}
	}

	private static void killTree(Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
		try {
			process.waitFor(KILL_GRACE_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static Path createLogFile(Path outputDir) {
		try {
			Path parent = outputDir.getParent() != null ? outputDir.getParent() : outputDir;
			return Files.createTempFile(parent, "ffmpeg-", ".log");
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao preparar log do ffmpeg em " + outputDir, e);
		}
	}

	/** Só o começo do log entra na mensagem de erro — o arquivo em si é limitado pelo ffmpeg em loglevel error. */
	private static String readLog(Path logFile) {
		try {
			byte[] bytes = Files.readAllBytes(logFile);
			int length = Math.min(bytes.length, MAX_LOG_BYTES);
			String text = new String(bytes, 0, length, StandardCharsets.UTF_8).strip();
			return bytes.length > MAX_LOG_BYTES ? text + " [log truncado]" : text;
		} catch (IOException e) {
			return "(log indisponível: " + e.getMessage() + ")";
		}
	}

	private static void ensureFramesWereProduced(Path outputDir, Path videoFile) {
		try (Stream<Path> files = Files.list(outputDir)) {
			if (files.findAny().isEmpty()) {
				throw new FfmpegProcessingException("Nenhum frame extraído — vídeo pode estar corrompido: " + videoFile);
			}
		} catch (IOException e) {
			throw new FfmpegProcessingException("Falha ao ler diretório de frames " + outputDir, e);
		}
	}
}
