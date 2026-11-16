package com.example.usermanagement.service;

import com.example.usermanagement.config.CacheConfig;
import com.example.usermanagement.dto.group.GroupRequest;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.exception.DuplicateResourceException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.UserGroup;
import com.example.usermanagement.repository.UserGroupRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final UserGroupRepository groupRepository;

    public GroupService(UserGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Cacheable(CacheConfig.GROUPS)
    public List<GroupResponse> findAll() {
        return groupRepository.findAllByOrderByNameAsc().stream().map(GroupResponse::from).toList();
    }

    public GroupResponse get(Long id) {
        return GroupResponse.from(getEntity(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.GROUPS, allEntries = true)
    public GroupResponse create(GroupRequest request) {
        String name = request.name().trim();
        if (groupRepository.existsByName(name)) {
            throw new DuplicateResourceException("Group '%s' already exists".formatted(name));
        }
        UserGroup group = new UserGroup();
        group.setName(name);
        group.setDescription(request.description());
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.GROUPS, allEntries = true)
    public GroupResponse update(Long id, GroupRequest request) {
        UserGroup group = getEntity(id);
        String name = request.name().trim();
        if (groupRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Group '%s' already exists".formatted(name));
        }
        group.setName(name);
        group.setDescription(request.description());
        return GroupResponse.from(groupRepository.saveAndFlush(group));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.GROUPS, allEntries = true)
    public void delete(Long id) {
        groupRepository.delete(getEntity(id));
    }

    private UserGroup getEntity(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Group", id));
    }
}
