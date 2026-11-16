package com.example.usermanagement.service;

import com.example.usermanagement.config.CacheConfig;
import com.example.usermanagement.dto.permission.PermissionRequest;
import com.example.usermanagement.dto.permission.PermissionResponse;
import com.example.usermanagement.exception.DuplicateResourceException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.Permission;
import com.example.usermanagement.repository.PermissionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Cacheable(CacheConfig.PERMISSIONS)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllByOrderByNameAsc().stream().map(PermissionResponse::from).toList();
    }

    public PermissionResponse get(Long id) {
        return PermissionResponse.from(getEntity(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PERMISSIONS, allEntries = true)
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Permission '%s' already exists".formatted(request.name()));
        }
        Permission permission = new Permission();
        permission.setName(request.name());
        permission.setDescription(request.description());
        return PermissionResponse.from(permissionRepository.save(permission));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PERMISSIONS, allEntries = true)
    public PermissionResponse update(Long id, PermissionRequest request) {
        Permission permission = getEntity(id);
        if (permissionRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateResourceException("Permission '%s' already exists".formatted(request.name()));
        }
        permission.setName(request.name());
        permission.setDescription(request.description());
        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    /**
     * Also removes the permission from every role (ON DELETE CASCADE).
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PERMISSIONS, allEntries = true)
    public void delete(Long id) {
        permissionRepository.delete(getEntity(id));
    }

    private Permission getEntity(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Permission", id));
    }
}
