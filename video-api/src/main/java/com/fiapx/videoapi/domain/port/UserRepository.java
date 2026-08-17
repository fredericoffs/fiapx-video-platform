package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.User;
import java.util.Optional;

public interface UserRepository {

  User save(User user);

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
