package com.hades.services.service;

import com.hades.services.model.Role;
import com.hades.services.model.User;
import com.hades.services.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public Optional<User> getById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid);
    }

    public User registerUser(String name, String email, String firebaseUid) {
        User user = new User(name, email, firebaseUid, Role.USER);
        return userRepository.save(user);
    }

    public Optional<User> loginUser(String email) {
        return userRepository.findByEmail(email);
    }

    public User updateRole(UUID userId, Role newRole) {
        return userRepository.findById(userId).map(user -> {
            user.setRole(newRole);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User updateProfile(UUID userId, String name, String phone, String organization, String address) {
        return userRepository.findById(userId).map(user -> {
            if (name != null && !name.trim().isEmpty()) {
                user.setName(name.trim());
            }
            user.setPhone(phone);
            user.setOrganization(organization);
            user.setAddress(address);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> getByOrganization(String organization) {
        return userRepository.findByOrganization(organization);
    }

    public User updateOrganization(UUID userId, String organization) {
        return userRepository.findById(userId).map(user -> {
            user.setOrganization(organization);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void delete(UUID userId) {
        userRepository.deleteById(userId);
    }
}
