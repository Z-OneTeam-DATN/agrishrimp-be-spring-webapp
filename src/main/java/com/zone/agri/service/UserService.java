package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.Constants;
import com.zone.agri.dto.auth.AuthResponse;
import com.zone.agri.dto.auth.LoginRequest;
import com.zone.agri.dto.user.UserInDto;
import com.zone.agri.dto.user.UserOutDto;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.utils.JwtUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private static final String AUTHORITY_USER = "ROLE_USER";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final CustomUserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;
  private final AuthenticationManager authenticationManager;

  public User findByUserId(Long userId) {
    return userRepository.findById(userId).orElse(null);
  }

  public UserOutDto signup(UserInDto inDto) {
    if (userRepository.findByEmail(inDto.getEmail()).isPresent()) {
      throw new BadRequestException(Constants.ErrorCode.EMAIL_ALREADY_EXITED, inDto.getEmail());
    }
    User user = inDto.toEntity();
    Set<Role> roleList = new HashSet<>(Collections.singletonList(getAuthority(AUTHORITY_USER)));
    user.setRoles(roleList);
    user.setHashedPassword(passwordEncoder.encode(inDto.getPassword()));
    user = userRepository.save(user);
    return user.toUserOutDto();
  }

  public UserOutDto getMe() {
    Long myId = AuthUtils.getUserDetail().getId();
    User user = userRepository.findById(myId)
        .orElseThrow(() -> new RuntimeException("Invalid username or password"));
    return user.toUserOutDto();
  }

  public AuthResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
      UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

      String accessToken = jwtUtils.generateAccessToken(userDetails);
      String refreshToken = jwtUtils.generateRefreshToken(userDetails);

      return new AuthResponse(accessToken, refreshToken);

    } catch (AuthenticationException e) {
      userRepository.findByEmail(request.getEmail()).ifPresent(userRepository::save);
      throw new CustomAuthenticationException("Invalid email or password");
    }
  }

  private Role getAuthority(String roleName) {
    return roleRepository.findById(roleName)
        .orElse(roleRepository.save(new Role(roleName, true, false)));
  }
}
