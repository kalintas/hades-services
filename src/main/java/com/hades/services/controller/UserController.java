package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.hades.services.model.Role;
import com.hades.services.model.User;
import com.hades.services.service.UserService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", u.getId());
        userMap.put("name", u.getName());
        userMap.put("email", u.getEmail());
        userMap.put("role", u.getRole().name());
        userMap.put("phone", u.getPhone());
        userMap.put("organization", u.getOrganization());
        userMap.put("address", u.getAddress());
        return userMap;
    }

    @GetMapping
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> getAll(@AuthenticationPrincipal String uid) {
        Optional<User> currentUserOpt = userService.findByFirebaseUid(uid);
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User currentUser = currentUserOpt.get();

        List<User> users;
        if (currentUser.getRole() == Role.ADMIN) {
            // ADMIN sees all users
            users = userService.getAll();
        } else {
            // MANAGER sees only users in their organization (excluding ADMINs and other
            // MANAGERs)
            String org = currentUser.getOrganization();
            if (org == null || org.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            users = userService.getByOrganization(org).stream()
                    .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.MANAGER)
                    .toList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            result.add(userToMap(u));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> getById(@PathVariable UUID id, @AuthenticationPrincipal String uid) {
        Optional<User> currentUserOpt = userService.findByFirebaseUid(uid);
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User currentUser = currentUserOpt.get();

        Optional<User> targetUserOpt = userService.getById(id);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User targetUser = targetUserOpt.get();

        // MANAGERs can only see users in their organization
        if (currentUser.getRole() == Role.MANAGER) {
            String managerOrg = currentUser.getOrganization();
            String targetOrg = targetUser.getOrganization();
            if (managerOrg == null || !managerOrg.equals(targetOrg)) {
                return ResponseEntity.status(403).body("You can only view users in your organization");
            }
        }

        return ResponseEntity.ok(userToMap(targetUser));
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
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> updateRole(@PathVariable UUID id, @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal String uid) {
        try {
            Optional<User> currentUserOpt = userService.findByFirebaseUid(uid);
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            User currentUser = currentUserOpt.get();

            Optional<User> targetUserOpt = userService.getById(id);
            if (targetUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            User targetUser = targetUserOpt.get();

            String roleStr = payload.get("role");
            Role newRole = Role.valueOf(roleStr);

            // Cannot edit yourself
            if (currentUser.getId().equals(targetUser.getId())) {
                return ResponseEntity.status(403).body("You cannot change your own role");
            }

            // ADMINs cannot edit other ADMINs
            if (currentUser.getRole() == Role.ADMIN && targetUser.getRole() == Role.ADMIN) {
                return ResponseEntity.status(403).body("You cannot change another admin's role");
            }

            // MANAGERs restrictions
            if (currentUser.getRole() == Role.MANAGER) {
                String managerOrg = currentUser.getOrganization();
                String targetOrg = targetUser.getOrganization();
                if (managerOrg == null || !managerOrg.equals(targetOrg)) {
                    return ResponseEntity.status(403).body("You can only edit users in your organization");
                }
                // MANAGERs can only assign USER or PERSONNEL roles
                if (newRole == Role.MANAGER || newRole == Role.ADMIN) {
                    return ResponseEntity.status(403).body("You cannot promote users to MANAGER or ADMIN");
                }
                // MANAGERs cannot edit other MANAGERs or ADMINs
                if (targetUser.getRole() == Role.MANAGER || targetUser.getRole() == Role.ADMIN) {
                    return ResponseEntity.status(403).body("You cannot edit managers or admins");
                }
            }

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

    @PutMapping("/{id}/organization")
    @RolesAllowed({ "ADMIN", "MANAGER" })
    public ResponseEntity<?> updateOrganization(@PathVariable UUID id, @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal String uid) {
        try {
            Optional<User> currentUserOpt = userService.findByFirebaseUid(uid);
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            User currentUser = currentUserOpt.get();

            Optional<User> targetUserOpt = userService.getById(id);
            if (targetUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            User targetUser = targetUserOpt.get();

            String newOrganization = payload.get("organization");

            // Cannot edit ADMINs or MANAGERs if you're a MANAGER
            if (currentUser.getRole() == Role.MANAGER) {
                if (targetUser.getRole() == Role.MANAGER || targetUser.getRole() == Role.ADMIN) {
                    return ResponseEntity.status(403).body("You cannot edit managers or admins");
                }
                // MANAGERs can only edit users in their organization
                String managerOrg = currentUser.getOrganization();
                String targetOrg = targetUser.getOrganization();
                if (managerOrg == null || !managerOrg.equals(targetOrg)) {
                    return ResponseEntity.status(403).body("You can only edit users in your organization");
                }
            }

            // ADMINs cannot edit other ADMINs
            if (currentUser.getRole() == Role.ADMIN && targetUser.getRole() == Role.ADMIN) {
                if (!currentUser.getId().equals(targetUser.getId())) {
                    return ResponseEntity.status(403).body("You cannot edit another admin");
                }
            }

            User updated = userService.updateOrganization(id, newOrganization);
            return ResponseEntity.ok(userToMap(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ResponseEntity<?> delete(@PathVariable UUID id, @AuthenticationPrincipal String uid) {
        Optional<User> currentUserOpt = userService.findByFirebaseUid(uid);
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        User currentUser = currentUserOpt.get();

        Optional<User> targetUserOpt = userService.getById(id);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User targetUser = targetUserOpt.get();

        // Cannot delete yourself
        if (currentUser.getId().equals(targetUser.getId())) {
            return ResponseEntity.status(403).body("You cannot delete yourself");
        }

        // ADMINs cannot delete other ADMINs
        if (targetUser.getRole() == Role.ADMIN) {
            return ResponseEntity.status(403).body("You cannot delete another admin");
        }

        userService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        try {
            Optional<User> targetUserOpt = userService.getById(id);
            if (targetUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            User targetUser = targetUserOpt.get();

            String name = payload.get("name");
            String phone = payload.get("phone");
            String newOrganization = payload.get("organization");
            String address = payload.get("address");

            // If user already has an organization, don't allow them to change it via
            // profile
            // They can only set it once. Changes must go through /users/{id}/organization
            // by upper roles
            String currentOrg = targetUser.getOrganization();
            if (currentOrg != null && !currentOrg.trim().isEmpty()) {
                // Keep existing organization, ignore the new value
                newOrganization = currentOrg;
            }

            // ADMINs should not have organization
            if (targetUser.getRole() == Role.ADMIN) {
                newOrganization = null;
            }

            User updated = userService.updateProfile(id, name, phone, newOrganization, address);

            Map<String, Object> result = new HashMap<>();
            result.put("id", updated.getId());
            result.put("name", updated.getName());
            result.put("email", updated.getEmail());
            result.put("role", updated.getRole().name());
            result.put("phone", updated.getPhone());
            result.put("organization", updated.getOrganization());
            result.put("address", updated.getAddress());

            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
