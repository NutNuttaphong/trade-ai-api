package com.trade.stocks.controller;

import com.trade.stocks.entity.User;
import com.trade.stocks.service.AiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> askAi(@RequestBody ChatRequest request) {
        Long userId = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            userId = user.getId();
        }
        String reply = aiChatService.generateResponse(userId, request.getPrompt());
        
        Map<String, String> response = new HashMap<>();
        response.put("response", reply);
        return ResponseEntity.ok(response);
    }

    public static class ChatRequest {
        private String prompt;

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }
}
