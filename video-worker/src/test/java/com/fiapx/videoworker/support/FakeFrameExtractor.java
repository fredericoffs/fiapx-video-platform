package com.fiapx.videoworker.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fiapx.videoworker.domain.port.FrameExtractor;

@Component
@Primary
public class FakeFrameExtractor implements FrameExtractor {

	@Override
	public void extractFrames(Path videoFile, Path outputDir, int fps) {
		try {
			Files.createFile(outputDir.resolve("frame_0001.png"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
