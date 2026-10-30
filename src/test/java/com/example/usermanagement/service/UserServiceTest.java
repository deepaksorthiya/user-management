package com.example.usermanagement.service;

import com.example.usermanagement.dto.user.UserCreateRequest;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.dto.user.UserUpdateRequest;
import com.example.usermanagement.exception.DuplicateResourceException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.User;
import com.example.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("create: normalizes username/email, hashes the password and defaults active to true")
    void create_validRequest_savesNormalizedUserWithHashedPassword() {
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(
                new UserCreateRequest("NewUser", "New@Example.com", "Password@123", " New ", " User ", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(response.username()).isEqualTo("newuser");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("create: duplicate username is rejected and nothing is saved")
    void create_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("Alice", "other@example.com", "Password@123", "A", "B", true)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("create: duplicate email is rejected and nothing is saved")
    void create_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("someone", "Alice@Example.com", "Password@123", "A", "B", true)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("get: unknown id throws not found")
    void get_unknownId_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User with id 99 not found");
    }

    @Test
    @DisplayName("update: email already used by another user is rejected")
    void update_emailUsedByAnotherUser_throwsConflict() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(new User()));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", 5L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(5L,
                new UserUpdateRequest("taken@example.com", "A", "B", true)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
