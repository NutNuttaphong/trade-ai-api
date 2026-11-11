package com.trade.stocks.controller;

import com.trade.stocks.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AutoTradeRuleRepository autoTradeRepository;
    private final StockRepository stockRepository;

    public AdminController(UserRepository userRepository,
                           TransactionRepository transactionRepository,
                           AutoTradeRuleRepository autoTradeRepository,
                           StockRepository stockRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.autoTradeRepository = autoTradeRepository;
        this.stockRepository = stockRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats() {
        long totalUsers = userRepository.count();
        long totalTransactions = transactionRepository.count();
        long totalActiveRules = autoTradeRepository.findByStatus("ACTIVE").size();
        long totalStocks = stockRepository.count();
        
        BigDecimal totalUserBalances = userRepository.findAll().stream()
                .map(user -> user.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalTransactions", totalTransactions);
        stats.put("totalActiveRules", totalActiveRules);
        stats.put("totalStocks", totalStocks);
        stats.put("totalUserBalances", totalUserBalances);
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        var userList = userRepository.findAll().stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("role", user.getRole());
            map.put("balance", user.getBalance());
            return map;
        }).toList();
        return ResponseEntity.ok(userList);
    }

    @PostMapping("/users/{userId}/balance")
    public ResponseEntity<?> adjustUserBalance(@PathVariable Long userId, @RequestBody Map<String, BigDecimal> body) {
        BigDecimal amount = body.get("amount");
        if (amount == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount is required"));
        }
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var user = userOpt.get();
        BigDecimal newBalance = user.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Balance cannot be negative"));
        }
        user.setBalance(newBalance);
        userRepository.save(user);

        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("username", user.getUsername());
        res.put("balance", user.getBalance());
        res.put("message", "Balance updated successfully");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getAllTransactions() {
        return ResponseEntity.ok(transactionRepository.findAllByOrderByTimestampDesc());
    }
}
