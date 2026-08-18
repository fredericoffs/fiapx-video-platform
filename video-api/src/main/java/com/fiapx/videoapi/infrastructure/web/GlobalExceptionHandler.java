package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.domain.exception.EmailAlreadyRegisteredException;
import com.fiapx.videoapi.domain.exception.InvalidCredentialsException;
import com.fiapx.videoapi.domain.exception.LoginRateLimitExceededException;
import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import com.fiapx.videoapi.domain.exception.VideoNotCompletedException;
import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(VideoNotFoundException.class)
  public ResponseEntity<String> handleVideoNotFound(VideoNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
  }

  @ExceptionHandler(VideoNotCompletedException.class)
  public ResponseEntity<String> handleVideoNotCompleted(VideoNotCompletedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ResponseEntity<String> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
  }

  @ExceptionHandler(LoginRateLimitExceededException.class)
  public ResponseEntity<String> handleLoginRateLimitExceeded(LoginRateLimitExceededException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
  }

  @ExceptionHandler(UnsupportedVideoFormatException.class)
  public ResponseEntity<String> handleUnsupportedVideoFormat(UnsupportedVideoFormatException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }
}
