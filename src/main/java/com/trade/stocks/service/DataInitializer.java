package com.trade.stocks.service;

import com.trade.stocks.entity.PortfolioItem;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.entity.User;
import com.trade.stocks.repository.PortfolioItemRepository;
import com.trade.stocks.repository.StockRepository;
import com.trade.stocks.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final PortfolioItemRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           StockRepository stockRepository,
                           PortfolioItemRepository portfolioRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Initialize Default Users
        if (userRepository.count() == 0) {
            User user = new User();
            user.setUsername("nutTest1234");
            user.setPassword(passwordEncoder.encode("nutTest1234"));
            user.setRole("USER");
            user.setBalance(new BigDecimal("100000.00"));
            userRepository.save(user);

            User admin = new User();
            admin.setUsername("nutAdmid1234");
            admin.setPassword(passwordEncoder.encode("nutAdmid1234"));
            admin.setRole("ADMIN");
            admin.setBalance(new BigDecimal("500000.00"));
            userRepository.save(admin);
        }

        // Initialize Default Stocks
        if (stockRepository.count() == 0) {
            stockRepository.save(new Stock(null, "AAPL", "Apple Inc.", new BigDecimal("175.50"), 0.15));
            stockRepository.save(new Stock(null, "GOOGL", "Alphabet Inc.", new BigDecimal("150.25"), -0.45));
            stockRepository.save(new Stock(null, "MSFT", "Microsoft Corp.", new BigDecimal("420.10"), 0.85));
            stockRepository.save(new Stock(null, "TSLA", "Tesla Inc.", new BigDecimal("185.00"), -1.20));
            stockRepository.save(new Stock(null, "AMZN", "Amazon.com Inc.", new BigDecimal("180.75"), 0.30));
            stockRepository.save(new Stock(null, "NVDA", "NVIDIA Corp.", new BigDecimal("900.00"), 2.50));
        }

        // Seed some Portfolio Items for the test user
        if (portfolioRepository.count() == 0) {
            User user = userRepository.findByUsername("nutTest1234").orElse(null);
            if (user != null) {
                portfolioRepository.save(new PortfolioItem(null, user.getId(), "AAPL", 10, new BigDecimal("170.00")));
                portfolioRepository.save(new PortfolioItem(null, user.getId(), "TSLA", 5, new BigDecimal("190.00")));
                portfolioRepository.save(new PortfolioItem(null, user.getId(), "NVDA", 2, new BigDecimal("850.00")));
            }
        }
    }
}
