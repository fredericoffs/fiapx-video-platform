package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.VideoDownload;
import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.application.usecase.DownloadVideoUseCase;
import com.fiapx.videoapi.application.usecase.GetVideoStatusUseCase;
import com.fiapx.videoapi.application.usecase.ListVideosUseCase;
import com.fiapx.videoapi.application.usecase.RequestVideoProcessingUseCase;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.web.dto.VideoListResponse;
import com.fiapx.videoapi.infrastructure.web.dto.VideoStatusResponse;
import com.fiapx.videoapi.infrastructure.web.dto.VideoUploadResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/videos")
public class VideoController {

  private final RequestVideoProcessingUseCase requestVideoProcessingUseCase;
  private final GetVideoStatusUseCase getVideoStatusUseCase;
  private final ListVideosUseCase listVideosUseCase;
  private final DownloadVideoUseCase downloadVideoUseCase;

  public VideoController(
      RequestVideoProcessingUseCase requestVideoProcessingUseCase,
      GetVideoStatusUseCase getVideoStatusUseCase,
      ListVideosUseCase listVideosUseCase,
      DownloadVideoUseCase downloadVideoUseCase
  ) {
    this.requestVideoProcessingUseCase = requestVideoProcessingUseCase;
    this.getVideoStatusUseCase = getVideoStatusUseCase;
    this.listVideosUseCase = listVideosUseCase;
    this.downloadVideoUseCase = downloadVideoUseCase;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<VideoUploadResponse> upload(
      @AuthenticationPrincipal UUID userId,
      @RequestParam("file") MultipartFile file
  ) throws IOException {
    VideoUploadCommand command = new VideoUploadCommand(
        userId,
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType()
    );
    VideoUploadResult result = requestVideoProcessingUseCase.handle(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(VideoUploadResponse.from(result));
  }

  @GetMapping
  public VideoListResponse list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) VideoStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    PageResult<Video> result = listVideosUseCase.handle(userId, status, page, size);
    return VideoListResponse.from(result);
  }

  @GetMapping("/{id}")
  public VideoStatusResponse getStatus(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    Video video = getVideoStatusUseCase.handle(id, userId);
    return VideoStatusResponse.from(video);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<InputStreamResource> download(
      @PathVariable UUID id,
      @AuthenticationPrincipal UUID userId
  ) {
    VideoDownload download = downloadVideoUseCase.handle(id, userId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
        .body(new InputStreamResource(download.content()));
  }
}
