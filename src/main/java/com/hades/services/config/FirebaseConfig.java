package com.hades.services.config;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials}")
    private String serviceAccountCredentialsBase64;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        byte[] decodedCredentials = Base64.getDecoder().decode(serviceAccountCredentialsBase64);
        InputStream credentials = new ByteArrayInputStream(decodedCredentials);

        FirebaseOptions firebaseOptions = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(credentials))
                .build();

        return FirebaseApp.initializeApp(firebaseOptions);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
