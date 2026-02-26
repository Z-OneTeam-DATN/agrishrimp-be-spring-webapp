package com.zone.agri.dto.user;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String phoneNumber;
    private String gender; // MALE, FEMALE, OTHER
    private LocalDate dateOfBirth;
}