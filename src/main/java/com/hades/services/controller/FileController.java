package com.hades.services.controller;

import com.hades.services.model.PresignedUrl;
import com.hades.services.model.PresignedUrlRequest;
import com.hades.services.service.AwsFileService;
import com.hades.services.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final AwsFileService awsFileService;
    private final UserService userService;

    @Value("${hades.file.service.max-upload-bytes:10485760}") // Default 10MB
    private int maxUploadBytes;

    private static final Set<String> ALLOWED_FOLDERS = Set.of("reports", "chats");

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrl> generateUrl(@AuthenticationPrincipal String uid,
            @RequestBody PresignedUrlRequest request) {
        if (request.getFileSize() > maxUploadBytes) {
            return ResponseEntity.badRequest().build();
        }

        if (request.getFolder() == null || !ALLOWED_FOLDERS.contains(request.getFolder())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            if (uid != null && !uid.equals("anonymousUser")) {
                userService.loginUser(uid); // Ensure user exists if authenticated
            } else {
                // Anonymous users can ONLY upload to "chats"
                if (!"chats".equals(request.getFolder())) {
                    return ResponseEntity.badRequest().build();
                }
            }

            String fileId = UUID.randomUUID().toString();
            String filePath = String.format("%s/%s", request.getFolder(), fileId);
            PresignedUrl url = awsFileService.generatePutPresignedUrl(filePath, (int) request.getFileSize());

            return ResponseEntity.ok(url);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
