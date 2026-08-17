package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.persistence.entity.UserEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.UserMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryAdapter implements UserRepository {

  private final SpringDataUserRepository springDataUserRepository;

  public UserRepositoryAdapter(SpringDataUserRepository springDataUserRepository) {
    this.springDataUserRepository = springDataUserRepository;
  }

  @Override
  public User save(User user) {
    UserEntity entity = UserMapper.toEntity(user);
    UserEntity saved = springDataUserRepository.save(entity);
    return UserMapper.toDomain(saved);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return springDataUserRepository.findByEmail(email).map(UserMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return springDataUserRepository.existsByEmail(email);
  }
}
