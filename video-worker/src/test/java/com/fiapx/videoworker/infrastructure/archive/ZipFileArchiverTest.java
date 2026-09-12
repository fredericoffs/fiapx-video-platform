package com.fiapx.videoworker.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipFileArchiverTest {

  @TempDir
  Path tempDir;

  private final ZipFileArchiver archiver = new ZipFileArchiver();

  @Test
  void zipsEveryFileOfTheDirectoryInSortedOrderWithContents() throws IOException {
    Path frames = Files.createDirectory(tempDir.resolve("frames"));
    Files.writeString(frames.resolve("frame_0002.png"), "segundo");
    Files.writeString(frames.resolve("frame_0001.png"), "primeiro");
    Path zip = tempDir.resolve("out.zip");

    archiver.zip(frames, zip);

    List<String> names = new ArrayList<>();
    List<String> contents = new ArrayList<>();
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        names.add(entry.getName());
        contents.add(new String(in.readAllBytes()));
      }
    }
    assertThat(names).containsExactly("frame_0001.png", "frame_0002.png");
    assertThat(contents).containsExactly("primeiro", "segundo");
  }

  @Test
  void producesEmptyZipForEmptyDirectory() throws IOException {
    Path frames = Files.createDirectory(tempDir.resolve("empty"));
    Path zip = tempDir.resolve("empty.zip");

    archiver.zip(frames, zip);

    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
      assertThat(in.getNextEntry()).isNull();
    }
  }

  @Test
  void wrapsIoFailureWhenDirectoryDoesNotExist() {
    Path missing = tempDir.resolve("missing");

    assertThatThrownBy(() -> archiver.zip(missing, tempDir.resolve("x.zip")))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("missing");
  }
}
