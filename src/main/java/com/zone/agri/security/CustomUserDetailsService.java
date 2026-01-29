package com.zone.agri.security;

import com.zone.agri.dto.user.UserDetail;
import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public CustomUserDetail loadUserByUsername(String email) throws UsernameNotFoundException {
    Optional<User> user = userRepository.findByEmail(email);
    if (user.isEmpty()) {
      throw new UsernameNotFoundException("Invalid username or password");
    }
    UserDetail userDetail = UserDetail.builder()
        .id(user.get().getId())
        .email(user.get().getEmail())
        .displayName(user.get().getDisplayName())
        .createdAt(user.get().getCreatedAt())
        .updatedAt(user.get().getUpdatedAt())
        .roleList(user.get().getRoleDtoList())
        .build();
    return new CustomUserDetail(email, user.get().getHashedPassword(), userDetail);
  }
}
