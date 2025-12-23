package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.hades.services.model.Role;
import com.hades.services.model.User;
import com.hades.services.service.UserService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generatePassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    @GetMapping
    @RolesAllowed("ADMIN")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<User> users = userService.getAll();

        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("name", user.getName());
            userMap.put("email", user.getEmail());
            userMap.put("role", user.getRole().name());
            result.add(userMap);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<User> getById(@PathVariable UUID id) {
        return userService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @RolesAllowed("ADMIN")
    public ResponseEntity<?> create(@RequestBody Map<String, String> payload) {
        try {
            String name = payload.get("name");
            String email = payload.get("email");
            String roleStr = payload.getOrDefault("role", "USER");
            Role role = Role.valueOf(roleStr);

            // Generate random password
            String password = generatePassword(12);

            // Create user in Firebase
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(name)
                    .setEmailVerified(true);

            UserRecord firebaseUser = FirebaseAuth.getInstance().createUser(request);

            // Save user to local database
            User user = new User(name, email, firebaseUser.getUid(), role);
            User savedUser = userService.save(user);

            // Return user info with password (only shown once)
            Map<String, Object> result = new HashMap<>();
            result.put("id", savedUser.getId());
            result.put("name", savedUser.getName());
            result.put("email", savedUser.getEmail());
            result.put("role", savedUser.getRole().name());
            result.put("password", password); // Show password only during creation

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid role: " + payload.get("role"));
        } catch (Exception e) {
            System.err.println("Failed to create user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to create user: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/role")
    @RolesAllowed("ADMIN")
    public ResponseEntity<?> updateRole(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        try {
            String roleStr = payload.get("role");
            Role newRole = Role.valueOf(roleStr);
            User updated = userService.updateRole(id, newRole);

            Map<String, Object> result = new HashMap<>();
            result.put("id", updated.getId());
            result.put("name", updated.getName());
            result.put("email", updated.getEmail());
            result.put("role", updated.getRole().name());

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid role: " + payload.get("role"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok().build();
    }
}
