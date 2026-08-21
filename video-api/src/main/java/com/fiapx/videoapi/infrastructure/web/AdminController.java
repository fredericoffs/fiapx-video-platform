package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.VideoWithOwner;
import com.fiapx.videoapi.application.usecase.DeleteUserUseCase;
import com.fiapx.videoapi.application.usecase.ListAllUsersUseCase;
import com.fiapx.videoapi.application.usecase.ListAllVideosUseCase;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.web.dto.AdminUserListResponse;
import com.fiapx.videoapi.infrastructure.web.dto.AdminVideoListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Endpoints restritos a administradores — controle sobre todos os usuários "
    + "e vídeos do sistema. Exige um JWT com papel ADMIN (ver SecurityConfig).")
public class AdminController {

  private final ListAllUsersUseCase listAllUsersUseCase;
  private final ListAllVideosUseCase listAllVideosUseCase;
  private final DeleteUserUseCase deleteUserUseCase;

  public AdminController(
      ListAllUsersUseCase listAllUsersUseCase,
      ListAllVideosUseCase listAllVideosUseCase,
      DeleteUserUseCase deleteUserUseCase
  ) {
    this.listAllUsersUseCase = listAllUsersUseCase;
    this.listAllVideosUseCase = listAllVideosUseCase;
    this.deleteUserUseCase = deleteUserUseCase;
  }

  @Operation(summary = "Lista todos os usuários cadastrados", description = "Paginado, sem escopo por dono.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = AdminUserListResponse.class))),
      @ApiResponse(responseCode = "403", description = "Usuário autenticado não é admin", content = @Content)
  })
  @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminUserListResponse listUsers(
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Itens por página") @RequestParam(defaultValue = "20") int size
  ) {
    PageResult<User> result = listAllUsersUseCase.handle(page, size);
    return AdminUserListResponse.from(result);
  }

  @Operation(
      summary = "Exclui um usuário",
      description = "Exclusão em cascata: apaga todos os vídeos do usuário (registro + arquivos no storage) antes "
          + "da própria conta."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Usuário excluído"),
      @ApiResponse(responseCode = "403", description = "Usuário autenticado não é admin", content = @Content),
      @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content)
  })
  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
    deleteUserUseCase.handle(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Lista todos os vídeos do sistema",
      description = "Paginado, de qualquer usuário, com o e-mail do dono de cada vídeo."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = AdminVideoListResponse.class))),
      @ApiResponse(responseCode = "403", description = "Usuário autenticado não é admin", content = @Content)
  })
  @GetMapping(value = "/videos", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminVideoListResponse listVideos(
      @Parameter(description = "Filtro opcional por status") @RequestParam(required = false) VideoStatus status,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Itens por página") @RequestParam(defaultValue = "20") int size
  ) {
    PageResult<VideoWithOwner> result = listAllVideosUseCase.handle(status, page, size);
    return AdminVideoListResponse.from(result);
  }
}
