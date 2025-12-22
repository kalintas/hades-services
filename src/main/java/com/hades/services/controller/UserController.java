package com.hades.services.controller;

import com.hades.services.model.Role;
import com.hades.services.model.User;
import com.hades.services.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        String roleName = payload.get("role");
        try {
            Role role = Role.valueOf(roleName.toUpperCase());
            return ResponseEntity.ok(userService.updateRole(id, role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
