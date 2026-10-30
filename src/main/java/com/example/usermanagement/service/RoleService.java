package com.example.usermanagement.service;

import com.example.usermanagement.dto.role.RoleRequest;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.exception.DuplicateResourceException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<RoleResponse> findAll() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(RoleResponse::from).toList();
    }

    public RoleResponse get(Long id) {
        return RoleResponse.from(getEntity(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Role '%s' already exists".formatted(request.name()));
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        return RoleResponse.from(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getEntity(id);
        if (roleRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateResourceException("Role '%s' already exists".formatted(request.name()));
        }
        role.setName(request.name());
        role.setDescription(request.description());
        return RoleResponse.from(roleRepository.saveAndFlush(role));
    }

    /** Also removes the role from every user, group and permission link (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long id) {
        roleRepository.delete(getEntity(id));
    }

    private Role getEntity(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }
}
