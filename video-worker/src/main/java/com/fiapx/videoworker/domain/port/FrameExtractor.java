package com.fiapx.videoworker.domain.port;

import java.nio.file.Path;

public interface FrameExtractor {

	void extractFrames(Path videoFile, Path outputDir, int fps);
}
