package com.fiapx.videoapi.domain.service;

import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import java.util.Locale;
import java.util.Set;

public final class VideoFormatValidator {

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv", "webm");

  private VideoFormatValidator() {
  }

  public static void validate(String originalFilename) {
    String extension = extractExtension(originalFilename);
    if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
      throw new UnsupportedVideoFormatException(originalFilename);
    }
  }

  private static String extractExtension(String filename) {
    if (filename == null) {
      return "";
    }
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == filename.length() - 1) {
      return "";
    }
    return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
  }
}
