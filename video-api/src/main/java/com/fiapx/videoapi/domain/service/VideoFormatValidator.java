package com.fiapx.videoapi.domain.service;

import com.fiapx.videoapi.domain.exception.InvalidFilenameException;
import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import java.util.Locale;
import java.util.Set;

/**
 * Valida o nome do arquivo enviado pelo usuário na borda. O nome original nunca vira caminho
 * em disco (o worker grava como {@code input.<ext>}) nem chave de storage (a API usa
 * {@code raw/<videoId>/source.<ext>}); ele fica só como metadado. Mesmo assim, rejeito aqui
 * nomes com separadores, {@code ..}, caracteres de controle ou tamanho excessivo, para não
 * depender da sanitização do navegador nem de quem consome o metadado depois.
 */
public final class VideoFormatValidator {

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv", "webm");
  private static final int MAX_FILENAME_LENGTH = 255;

  private VideoFormatValidator() {
  }

  public static void validate(String originalFilename) {
    validatedExtension(originalFilename);
  }

  /** Extensão validada, em minúsculas — a única parte do nome original que entra na chave de storage. */
  public static String validatedExtension(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new UnsupportedVideoFormatException(String.valueOf(originalFilename));
    }
    validateFilenameShape(originalFilename);
    String extension = extractExtension(originalFilename);
    if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
      throw new UnsupportedVideoFormatException(originalFilename);
    }
    return extension;
  }

  private static void validateFilenameShape(String filename) {
    if (filename.length() > MAX_FILENAME_LENGTH) {
      throw new InvalidFilenameException("excede " + MAX_FILENAME_LENGTH + " caracteres");
    }
    if (filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
      throw new InvalidFilenameException("contém separador de diretório");
    }
    if (filename.equals("..") || filename.startsWith("../") || filename.contains("/..")
        || filename.equals(".") || filename.startsWith("..")) {
      throw new InvalidFilenameException("contém referência a diretório pai");
    }
    for (int i = 0; i < filename.length(); i++) {
      char c = filename.charAt(i);
      if (c < 0x20 || c == 0x7F) {
        throw new InvalidFilenameException("contém caractere de controle");
      }
    }
  }

  private static String extractExtension(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
      return "";
    }
    return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
  }
}
