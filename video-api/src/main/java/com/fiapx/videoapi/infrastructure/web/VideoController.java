package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.VideoDownload;
import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.application.usecase.DeleteVideoUseCase;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/videos")
@Tag(name = "Videos", description = "Upload, listagem, consulta de status e download de vídeos. "
    + "Todos os endpoints exigem Bearer JWT e só enxergam vídeos do próprio usuário autenticado (vídeo de outro usuário é tratado como inexistente).")
public class VideoController {

  private final RequestVideoProcessingUseCase requestVideoProcessingUseCase;
  private final GetVideoStatusUseCase getVideoStatusUseCase;
  private final ListVideosUseCase listVideosUseCase;
  private final DownloadVideoUseCase downloadVideoUseCase;
  private final DeleteVideoUseCase deleteVideoUseCase;

  public VideoController(
      RequestVideoProcessingUseCase requestVideoProcessingUseCase,
      GetVideoStatusUseCase getVideoStatusUseCase,
      ListVideosUseCase listVideosUseCase,
      DownloadVideoUseCase downloadVideoUseCase,
      DeleteVideoUseCase deleteVideoUseCase
  ) {
    this.requestVideoProcessingUseCase = requestVideoProcessingUseCase;
    this.getVideoStatusUseCase = getVideoStatusUseCase;
    this.listVideosUseCase = listVideosUseCase;
    this.downloadVideoUseCase = downloadVideoUseCase;
    this.deleteVideoUseCase = deleteVideoUseCase;
  }

  @Operation(
      summary = "Envia um vídeo para processamento",
      description = "Faz upload de um arquivo de vídeo (multipart), persiste com status QUEUED e publica um evento "
          + "assíncrono para o video-worker extrair os frames. Formatos aceitos: mp4, mov, avi, mkv, webm (ver VideoFormatValidator)."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Vídeo aceito e enfileirado para processamento",
          content = @Content(schema = @Schema(implementation = VideoUploadResponse.class))),
      @ApiResponse(responseCode = "400", description = "Formato de vídeo não suportado", content = @Content),
      @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content)
  })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VideoUploadResponse> upload(
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Arquivo de vídeo (mp4, mov, avi, mkv ou webm)", required = true)
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

  @Operation(
      summary = "Lista os vídeos do usuário autenticado",
      description = "Listagem paginada, opcionalmente filtrada por status. Nunca retorna vídeos de outros usuários."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = VideoListResponse.class))),
      @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content)
  })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public VideoListResponse list(
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Filtro opcional por status") @RequestParam(required = false) VideoStatus status,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Itens por página") @RequestParam(defaultValue = "20") int size
  ) {
    PageResult<Video> result = listVideosUseCase.handle(userId, status, page, size);
    return VideoListResponse.from(result);
  }

  @Operation(
      summary = "Consulta o status de um vídeo",
      description = "404 tanto para vídeo inexistente quanto para vídeo de outro usuário — não vaza a existência de um recurso alheio."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = VideoStatusResponse.class))),
      @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content),
      @ApiResponse(responseCode = "404", description = "Vídeo não encontrado (ou pertence a outro usuário)",
          content = @Content)
  })
  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public VideoStatusResponse getStatus(
      @Parameter(description = "ID do vídeo", in = ParameterIn.PATH) @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId
  ) {
    Video video = getVideoStatusUseCase.handle(id, userId);
    return VideoStatusResponse.from(video);
  }

  @Operation(
      summary = "Baixa o zip de frames de um vídeo já processado",
      description = "Retorna o zip como application/octet-stream, transmitido diretamente do storage (proxy de bytes) — não uma URL pré-assinada."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Zip com os frames extraídos",
          content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary"))),
      @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content),
      @ApiResponse(responseCode = "404", description = "Vídeo não encontrado (ou pertence a outro usuário)",
          content = @Content),
      @ApiResponse(responseCode = "409", description = "Vídeo ainda não está com status COMPLETED",
          content = @Content)
  })
  @GetMapping("/{id}/download")
  public ResponseEntity<InputStreamResource> download(
      @Parameter(description = "ID do vídeo", in = ParameterIn.PATH) @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId
  ) {
    VideoDownload download = downloadVideoUseCase.handle(id, userId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
        .body(new InputStreamResource(download.content()));
  }

  @Operation(
      summary = "Exclui um vídeo",
      description = "Remove o registro e os arquivos no storage (original e, se existir, o zip processado). "
          + "Um administrador pode excluir vídeo de qualquer usuário; um usuário comum só o próprio."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Vídeo excluído"),
      @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content),
      @ApiResponse(responseCode = "404", description = "Vídeo não encontrado (ou pertence a outro usuário)",
          content = @Content)
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "ID do vídeo", in = ParameterIn.PATH) @PathVariable UUID id,
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId,
      Authentication authentication
  ) {
    boolean isAdmin = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    deleteVideoUseCase.handle(id, userId, isAdmin);
    return ResponseEntity.noContent().build();
  }
}
