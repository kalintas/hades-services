package com.hades.services.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hades.services.security.annotation.Access;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*") // Allow requests from frontend
public class ChatController {

    @PostMapping
    @Access.Public
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        String responseText = "Anlaşılmadı, lütfen tekrar edin.";

        if (message != null) {
            String lowerMessage = message.toLowerCase();

            if (lowerMessage.contains("merhaba") || lowerMessage.contains("selam")) {
                responseText = "Merhaba! Size nasıl yardımcı olabilirim? Deprem güvenliği, hasar tespiti veya acil durum prosedürleri hakkında sorularınızı yanıtlayabilirim.";
            } else if (lowerMessage.contains("hasar") || lowerMessage.contains("çatlak")) {
                responseText = "Hasar tespiti yapmak için binanın hasarlı bölgesinin fotoğrafını yükleyebilir misiniz? Fotoğraf üzerinden ön değerlendirme yapabilirim.";
            } else if (lowerMessage.contains("deprem") || lowerMessage.contains("sarsıntı")) {
                responseText = "Deprem anında 'Çök-Kapan-Tutun' pozisyonunu almalısınız. Sarsıntı geçtikten sonra binayı güvenli bir şekilde tahliye edin ve toplanma alanlarına gidin.";
            } else if (lowerMessage.contains("drone") || lowerMessage.contains("görüntü")) {
                responseText = "Drone görüntülerini analiz ederek geniş alanlardaki hasarı haritalandırabilir ve ulaşılması zor bölgelerdeki yıkımı tespit edebilirim.";
            } else if (lowerMessage.contains("acil") || lowerMessage.contains("112") || lowerMessage.contains("afad")
                    || lowerMessage.contains("telefon") || lowerMessage.contains("numara")) {
                responseText = "🚨 **Acil Durum Numaraları:**\n- **112**: Acil Çağrı Merkezi (Ambulans, Polis, İtfaiye)\n- **122**: AFAD\n- **177**: Orman Yangını İhbar\nLütfen hattı gereksiz meşgul etmeyiniz.";
            } else if (lowerMessage.contains("toplanma") || lowerMessage.contains("alan")
                    || lowerMessage.contains("nerede") || lowerMessage.contains("konum")) {
                responseText = "📍 Size en yakın toplanma alanını e-Devlet üzerinden 'Afet ve Acil Durum Toplanma Alanı Sorgulama' hizmetini kullanarak öğrenebilirsiniz. Güvenliğiniz için lütfen hasarlı binalardan uzak durun.";
            } else if (lowerMessage.contains("yardım") || lowerMessage.contains("ilk yardım")
                    || lowerMessage.contains("yaralı") || lowerMessage.contains("kanama")) {
                if (lowerMessage.contains("ilk") || lowerMessage.contains("yaralı")) {
                    responseText = "🩹 **Temel İlk Yardım:**\n1. Önce kendi güvenliğinizi sağlayın.\n2. Yaralını hareket ettirmeyin (hayati tehlike yoksa).\n3. Kanama varsa temiz bir bezle baskı uygulayın.\n4. Yaralıyı sıcak tutun ve hemen 112'yi arayın.";
                } else {
                    responseText = "ℹ️ **Size şu konularda yardımcı olabilirim:**\n- 'Hasar bildir' yazarak fotoğraf yükleyebilirsiniz.\n- 'Acil numaralar' yazarak iletişim listesini görebilirsiniz.\n- 'Deprem anında ne yapmalıyım?' diye sorabilirsiniz.\n- 'Toplanma alanı' hakkında bilgi alabilirsiniz.";
                }
            } else if (lowerMessage.contains("teşekkür") || lowerMessage.contains("sağol")) {
                responseText = "Rica ederim. Lütfen dikkatli olun ve güvende kalın. 🙏";
            } else {
                responseText = "Bu konuda size şu an yardımcı olamıyorum. 'Yardım' yazarak neler yapabileceğimi görebilirsiniz.";
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("response", responseText);

        return ResponseEntity.ok(response);
    }
}
