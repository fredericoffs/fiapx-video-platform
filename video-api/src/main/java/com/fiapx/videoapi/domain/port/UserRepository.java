package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  User save(User user);

  Optional<User> findByEmail(String email);

  Optional<User> findById(UUID id);

  default Optional<User> findByIdForUpdate(UUID id) {
    return findById(id);
  }

  boolean existsByEmail(String email);

  PageResult<User> findAll(String emailFilter, int page, int size);

  void deleteById(UUID id);
}
