package com.example.usermanagement.controller;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.common.ScrollResponse;
import com.example.usermanagement.dto.user.PasswordChangeRequest;
import com.example.usermanagement.dto.user.UserCreateRequest;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.dto.user.UserUpdateRequest;
import com.example.usermanagement.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * CRUD on users. Roles / groups / access of a user: {@link UserRelationController}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Offset pagination: GET /api/v1/users?q=ali&active=true&page=0&size=20&sort=username,asc
     * Sort is limited to id, username, email, createdAt. Search text needs at least 3 characters
     * (trigram indexes cannot help with shorter terms).
     */
    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) @Size(min = 3, max = 100) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return userService.search(q, active, pageable);
    }

    /**
     * Keyset pagination for big result sets / exports / infinite scroll:
     * GET /api/v1/users/scroll?size=100            -> first page
     * GET /api/v1/users/scroll?size=100&after=123  -> pass the previous response's nextCursor
     */
    @GetMapping("/scroll")
    public ScrollResponse<UserResponse> scroll(
            @RequestParam(required = false) @Size(min = 3, max = 100) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Positive Long after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return userService.scroll(q, active, after, size);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
