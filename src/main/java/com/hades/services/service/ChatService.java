package com.hades.services.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.core.dto.YoloParams;
import com.hades.services.model.ChatMessage;
import com.hades.services.model.ChatSession;
import com.hades.services.model.YoloInference;
import com.hades.services.repository.ChatMessageRepository;
import com.hades.services.repository.ChatSessionRepository;
import com.hades.services.repository.YoloInferenceRepository;
import com.hades.services.service.sagemaker.YoloInferenceService;
import com.hades.services.service.sagemaker.dto.SageMakerYoloResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final YoloInferenceService yoloInferenceService;
    private final AwsFileService awsFileService;
    private final ObjectMapper objectMapper;
    private final YoloInferenceRepository yoloInferenceRepository;

    @Value("${hades.content.domain}")
    private String contentDomain;

    // Session management
    public ChatSession createSession(UUID userId, String title) {
        ChatSession session = new ChatSession(userId, title);
        return chatSessionRepository.save(session);
    }

    public List<ChatSession> getSessions(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<ChatSession> getSession(UUID sessionId) {
        return chatSessionRepository.findById(sessionId);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    public void updateSessionTitle(UUID sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTitle(title);
            chatSessionRepository.save(session);
        });
    }

    // Message management
    public ChatMessage saveMessage(UUID sessionId, UUID userId, String role, String content, String imageUrl) {
        ChatMessage message = new ChatMessage(sessionId, userId, role, content, imageUrl);
        ChatMessage savedMessage = chatMessageRepository.save(message);

        if ("user".equals(role) && imageUrl != null && !imageUrl.isEmpty()) {
            CompletableFuture.runAsync(() -> processYoloInference(savedMessage.getId(), imageUrl));
        }

        return savedMessage;
    }

    private void processYoloInference(UUID messageId, String imageUrl) {
        try {
            String contentUrlPrefix = "https://" + contentDomain + "/";
            byte[] imageBytes = null;

            if (imageUrl.startsWith(contentUrlPrefix)) {
                String filePath = imageUrl.substring(contentUrlPrefix.length());
                imageBytes = awsFileService.downloadFile(filePath);
            } else if (imageUrl.startsWith("data:image")) {
                String base64 = imageUrl.substring(imageUrl.indexOf(",") + 1);
                imageBytes = Base64.getDecoder().decode(base64);
            } else {
                throw new IllegalArgumentException("Unsupported image URL format: " + imageUrl);
            }

            YoloParams params = new YoloParams();
            SageMakerYoloResponse response = yoloInferenceService.infer(imageBytes, params);

            YoloInference inference = new YoloInference();
            inference.setMessageId(messageId);
            inference.setDetectionsJson(objectMapper.writeValueAsString(response.detections()));
            inference.setImageWidth(response.imageWidth());
            inference.setImageHeight(response.imageHeight());
            inference.setInferenceTimeMs(response.inferenceTimeMs());
            inference.setError(response.error());

            yoloInferenceRepository.save(inference);

        } catch (Exception e) {
            System.err.println("Failed to process YOLO inference for message " + messageId + ": " + e.getMessage());
            e.printStackTrace();
            YoloInference inference = new YoloInference();
            inference.setMessageId(messageId);
            inference.setError(e.getMessage());
            yoloInferenceRepository.save(inference);
        }
    }

    public List<ChatMessage> getSessionMessages(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    // Response generation (mock)
    public String generateResponse(String message, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            return "Resimde 3 tane hasarli bina goruyorum.";
        }
        return generateResponse(message);
    }

    public String generateResponse(String message) {
        if (message == null) {
            return "Anlaşılmadı, lütfen tekrar edin.";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("merhaba") || lowerMessage.contains("selam")) {
            return "Merhaba! Size nasıl yardımcı olabilirim? Deprem güvenliği, hasar tespiti veya acil durum prosedürleri hakkında sorularınızı yanıtlayabilirim.";
        } else if (lowerMessage.contains("hasar") || lowerMessage.contains("çatlak")) {
            return "Hasar tespiti yapmak için binanın hasarlı bölgesinin fotoğrafını yükleyebilir misiniz? Fotoğraf üzerinden ön değerlendirme yapabilirim.";
        } else if (lowerMessage.contains("deprem") || lowerMessage.contains("sarsıntı")) {
            return "Deprem anında 'Çök-Kapan-Tutun' pozisyonunu almalısınız. Sarsıntı geçtikten sonra binayı güvenli bir şekilde tahliye edin ve toplanma alanlarına gidin.";
        } else if (lowerMessage.contains("drone") || lowerMessage.contains("görüntü")) {
            return "Drone görüntülerini analiz ederek geniş alanlardaki hasarı haritalandırabilir ve ulaşılması zor bölgelerdeki yıkımı tespit edebilirim.";
        } else if (lowerMessage.contains("acil") || lowerMessage.contains("112") || lowerMessage.contains("afad")
                || lowerMessage.contains("telefon") || lowerMessage.contains("numara")) {
            return "🚨 **Acil Durum Numaraları:**\n- **112**: Acil Çağrı Merkezi (Ambulans, Polis, İtfaiye)\n- **122**: AFAD\n- **177**: Orman Yangını İhbar\nLütfen hattı gereksiz meşgul etmeyiniz.";
        } else if (lowerMessage.contains("toplanma") || lowerMessage.contains("alan")
                || lowerMessage.contains("nerede") || lowerMessage.contains("konum")) {
            return "📍 Size en yakın toplanma alanını e-Devlet üzerinden 'Afet ve Acil Durum Toplanma Alanı Sorgulama' hizmetini kullanarak öğrenebilirsiniz. Güvenliğiniz için lütfen hasarlı binalardan uzak durun.";
        } else if (lowerMessage.contains("yardım") || lowerMessage.contains("ilk yardım")
                || lowerMessage.contains("yaralı") || lowerMessage.contains("kanama")) {
            if (lowerMessage.contains("ilk") || lowerMessage.contains("yaralı")) {
                return "🩹 **Temel İlk Yardım:**\n1. Önce kendi güvenliğinizi sağlayın.\n2. Yaralını hareket ettirmeyin (hayati tehlike yoksa).\n3. Kanama varsa temiz bir bezle baskı uygulayın.\n4. Yaralıyı sıcak tutun ve hemen 112'yi arayın.";
            } else {
                return "ℹ️ **Size şu konularda yardımcı olabilirim:**\n- 'Hasar bildir' yazarak fotoğraf yükleyebilirsiniz.\n- 'Acil numaralar' yazarak iletişim listesini görebilirsiniz.\n- 'Deprem anında ne yapmalıyım?' diye sorabilirsiniz.\n- 'Toplanma alanı' hakkında bilgi alabilirsiniz.";
            }
        } else if (lowerMessage.contains("teşekkür") || lowerMessage.contains("sağol")) {
            return "Rica ederim. Lütfen dikkatli olun ve güvende kalın. 🙏";
        } else {
            return "Bu konuda size şu an yardımcı olamıyorum. 'Yardım' yazarak neler yapabileceğimi görebilirsiniz.";
        }
    }
}
