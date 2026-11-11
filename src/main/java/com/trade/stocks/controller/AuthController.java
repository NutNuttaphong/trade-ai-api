package com.trade.stocks.controller;

import com.trade.stocks.config.TokenService;
import com.trade.stocks.entity.User;
import com.trade.stocks.repository.UserRepository;
import com.trade.stocks.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, TokenService tokenService, UserRepository userRepository) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<String> tokenOpt = authService.login(request.getUsername(), request.getPassword());
        
        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();
            User user = tokenService.validateToken(token);
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("role", user.getRole());
            response.put("balance", user.getBalance());
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            User user = (User) principal;
            // Always fetch fresh details from DB to get the exact real-time balance
            User freshUser = userRepository.findById(user.getId()).orElse(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", freshUser.getId());
            response.put("username", freshUser.getUsername());
            response.put("role", freshUser.getRole());
            response.put("balance", freshUser.getBalance());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
