package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.application.usecase.GetVideoStatusUseCase;
import com.fiapx.videoapi.application.usecase.RequestVideoProcessingUseCase;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.infrastructure.web.dto.VideoStatusResponse;
import com.fiapx.videoapi.infrastructure.web.dto.VideoUploadResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  public VideoController(
      RequestVideoProcessingUseCase requestVideoProcessingUseCase,
      GetVideoStatusUseCase getVideoStatusUseCase
  ) {
    this.requestVideoProcessingUseCase = requestVideoProcessingUseCase;
    this.getVideoStatusUseCase = getVideoStatusUseCase;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<VideoUploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
    VideoUploadCommand command = new VideoUploadCommand(
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType()
    );
    VideoUploadResult result = requestVideoProcessingUseCase.handle(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(VideoUploadResponse.from(result));
  }

  @GetMapping("/{id}")
  public VideoStatusResponse getStatus(@PathVariable UUID id) {
    Video video = getVideoStatusUseCase.handle(id);
    return VideoStatusResponse.from(video);
  }
}
