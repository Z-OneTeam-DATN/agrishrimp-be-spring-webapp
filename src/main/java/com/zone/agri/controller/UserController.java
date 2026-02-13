package com.zone.agri.controller;

import com.zone.agri.dto.admin.UserDTO;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/all-staff")
    public ResponseEntity<List<UserDTO>> getAllStaff() {
        List<UserDTO> staff = userRepository.findAll().stream()
                .map(user -> UserDTO.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .userCode(String.valueOf(user.getId()))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(staff);
    }
}