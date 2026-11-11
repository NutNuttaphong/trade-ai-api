package com.trade.stocks.config;

import com.trade.stocks.entity.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    // Maps token string to User entity
    private final Map<String, User> tokenMap = new ConcurrentHashMap<>();

    public String generateToken(User user) {
        // Clear previous tokens for the same user to prevent leaks
        tokenMap.entrySet().removeIf(entry -> entry.getValue().getUsername().equals(user.getUsername()));
        
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenMap.put(token, user);
        return token;
    }

    public User validateToken(String token) {
        return tokenMap.get(token);
    }

    public void invalidateToken(String token) {
        tokenMap.remove(token);
    }
}
