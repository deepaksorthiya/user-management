package com.example.usermanagement.service;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.user.PasswordChangeRequest;
import com.example.usermanagement.dto.user.UserCreateRequest;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.dto.user.UserUpdateRequest;
import com.example.usermanagement.exception.DuplicateResourceException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.User;
import com.example.usermanagement.repository.UserRepository;
import com.example.usermanagement.repository.UserSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** CRUD for users. Assignments (roles, groups) live in {@link UserRelationService}. */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserResponse> search(String q, Boolean active, Pageable pageable) {
        Page<User> page = userRepository.findAll(UserSpecifications.matching(q, active), pageable);
        return PageResponse.from(page.map(UserResponse::from));
    }

    public UserResponse get(Long id) {
        return UserResponse.from(getEntity(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '%s' is already taken".formatted(username));
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '%s' is already registered".formatted(email));
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setActive(request.active() == null || request.active());

        User saved = userRepository.save(user);
        log.info("Created user id={} username={}", saved.getId(), saved.getUsername());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = getEntity(id);
        String email = normalize(request.email());

        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new DuplicateResourceException("Email '%s' is already registered".formatted(email));
        }

        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setActive(request.active());

        // flush so the response carries the refreshed updatedAt
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        User user = getEntity(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("Password changed for user id={}", id);
    }

    @Transactional
    public void delete(Long id) {
        User user = getEntity(id);
        userRepository.delete(user);
        log.info("Deleted user id={}", id);
    }

    private User getEntity(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
