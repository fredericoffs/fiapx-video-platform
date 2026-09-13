package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.persistence.entity.UserEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.UserMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  public Optional<User> findById(UUID id) {
    return springDataUserRepository.findById(id).map(UserMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return springDataUserRepository.existsByEmail(email);
  }

  @Override
  public PageResult<User> findAll(String emailFilter, int page, int size) {
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<UserEntity> result = (emailFilter == null || emailFilter.isBlank())
        ? springDataUserRepository.findAll(pageRequest)
        : springDataUserRepository.findByEmailContainingIgnoreCase(emailFilter, pageRequest);
    List<User> items = result.getContent().stream().map(UserMapper::toDomain).toList();
    return new PageResult<>(items, page, size, result.getTotalElements());
  }

  @Override
  public void deleteById(UUID id) {
    springDataUserRepository.deleteById(id);
  }
}
