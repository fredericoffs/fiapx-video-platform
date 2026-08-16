package com.fiapx.videoworker.infrastructure.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.fiapx.videoworker.domain.port.Archiver;

@Component
public class ZipFileArchiver implements Archiver {

	@Override
	public void zip(Path directory, Path outputZip) {
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip));
				Stream<Path> files = Files.list(directory)) {
			for (Path file : files.sorted().toList()) {
				zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
				Files.copy(file, zos);
				zos.closeEntry();
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao compactar diretório " + directory, e);
		}
	}
}
