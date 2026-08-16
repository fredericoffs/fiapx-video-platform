package com.fiapx.videoworker.domain.port;

import java.nio.file.Path;

public interface Archiver {

	void zip(Path directory, Path outputZip);
}
