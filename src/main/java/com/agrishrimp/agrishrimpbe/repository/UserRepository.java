package com.agrishrimp.agrishrimpbe.repository;

import com.agrishrimp.agrishrimpbe.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);


    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);


    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByPhoneNumberAndIsDeletedFalse(String phoneNumber);
}
