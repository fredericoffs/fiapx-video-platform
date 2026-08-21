package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  User save(User user);

  Optional<User> findByEmail(String email);

  Optional<User> findById(UUID id);

  boolean existsByEmail(String email);
}
