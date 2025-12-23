package com.hades.services.controller;

import com.hades.services.model.User;
import com.hades.services.security.annotation.Access;
import com.hades.services.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // This endpoint assumes the frontend has already authenticated with Firebase
    // and sends the token in the header. The Filter verifies the token.
    // This endpoint is used to explicit sync or "login" to our backend to ensure
    // User entity exists.
    @Access.Public
    @PostMapping("/login")
    public ResponseEntity<User> login(@AuthenticationPrincipal String uid, Authentication authentication,
            HttpServletResponse response, HttpServletRequest request,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            // Try to find existing user
            User user = userService.findByFirebaseUid(uid).orElse(null);

            if (user == null && payload != null) {
                // User exists in Firebase but not in our DB - auto-create
                String email = payload.get("email");
                String name = payload.get("name");
                if (name == null || name.isEmpty()) {
                    name = email != null ? email.split("@")[0] : "User";
                }
                if (email != null) {
                    user = userService.registerUser(name, email, uid);
                }
            }

            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            setCookie(response, authentication, request);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Access.Public
    @PostMapping("/signup")
    public ResponseEntity<User> signup(@AuthenticationPrincipal String uid, @RequestBody Map<String, String> payload,
            Authentication authentication, HttpServletResponse response, HttpServletRequest request) {
        String email = payload.get("email");
        String name = payload.get("name");
        try {
            User user = userService.registerUser(name, email, uid);
            setCookie(response, authentication, request);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(@AuthenticationPrincipal String uid) {
        try {
            return userService.findByFirebaseUid(uid)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(401).build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie
                .from("hades_session", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0) // Expire immediately
                .sameSite("Lax")
                .build();

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok().build();
    }

    private void setCookie(HttpServletResponse response, Authentication authentication, HttpServletRequest request) {
        String token = null;

        // Try getting token from header first
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            token = headerAuth.substring(7);
        }
        // Should never happen
        if (token == null) {
            throw new RuntimeException("No token found");
        }
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie
                .from("hades_session", token)
                .httpOnly(true)
                .path("/")
                .maxAge(3600)
                .sameSite("Lax")
                .build();

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
