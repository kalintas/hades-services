package com.hades.services.service;

import com.hades.services.model.Role;
import com.hades.services.model.User;
import com.hades.services.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid);
    }

    public User registerUser(String name, String email, String firebaseUid) {
        if (userRepository.findByFirebaseUid(firebaseUid).isPresent()
                || userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Name is empty");
        }

        User newUser = new User(name, email, firebaseUid, Role.USER);
        return userRepository.save(newUser);
    }

    public User loginUser(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("User not found. Please sign up first."));
    }

    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateRole(java.util.UUID userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    public void deleteUser(java.util.UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
        userRepository.deleteById(userId);
    }
}
