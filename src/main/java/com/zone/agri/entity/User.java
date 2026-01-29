package com.zone.agri.entity;

import com.zone.agri.dto.user.RoleDto;
import com.zone.agri.dto.user.UserOutDto;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  Long id;
  @Column(nullable = false, unique = true)
  String email;
  @Column(nullable = false)
  String hashedPassword;
  String displayName;

  @CreatedDate
  @Column(updatable = false)
  LocalDateTime createdAt;
  @LastModifiedDate
  LocalDateTime updatedAt;

  @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @JoinTable(name = "user_role",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_name"))
  Set<Role> roles;

  public Set<RoleDto> getRoleDtoList() {
    return this.getRoles()
        .stream()
        .map(RoleDto::new)
        .collect(Collectors.toSet());
  }

  public UserOutDto toUserOutDto() {
    return UserOutDto.builder()
        .id(this.getId())
        .displayName(this.getDisplayName())
        .email(this.getEmail())
        .createdAt(this.getCreatedAt())
        .updatedAt(this.getUpdatedAt())
        .roles(this.getRoleDtoList())
        .build();
  }
}
