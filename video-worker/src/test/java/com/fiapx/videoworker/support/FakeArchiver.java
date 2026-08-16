package com.fiapx.videoworker.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fiapx.videoworker.domain.port.Archiver;

@Component
@Primary
public class FakeArchiver implements Archiver {

	@Override
	public void zip(Path directory, Path outputZip) {
		try {
			Files.write(outputZip, "fake-zip-bytes".getBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
