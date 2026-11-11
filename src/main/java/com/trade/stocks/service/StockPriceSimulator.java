package com.trade.stocks.service;

import com.trade.stocks.config.StockWebSocketHandler;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.repository.StockRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

@Service
public class StockPriceSimulator {

    private final StockRepository stockRepository;
    private final AutoTradeEngine autoTradeEngine;
    private final StockWebSocketHandler webSocketHandler;
    private final Random random = new Random();

    public StockPriceSimulator(StockRepository stockRepository,
                               AutoTradeEngine autoTradeEngine,
                               StockWebSocketHandler webSocketHandler) {
        this.stockRepository = stockRepository;
        this.autoTradeEngine = autoTradeEngine;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void simulateMarketTicks() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"TICK\",\"data\":[");

        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            
            // Random variation between -1.5% and +1.5%
            double percentChange = (random.nextDouble() * 3.0) - 1.5;
            BigDecimal multiplier = BigDecimal.valueOf(1.0 + (percentChange / 100.0));
            BigDecimal newPrice = stock.getCurrentPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            
            // Prevent prices from falling to zero
            if (newPrice.compareTo(BigDecimal.valueOf(1.00)) < 0) {
                newPrice = BigDecimal.valueOf(1.00 + random.nextDouble() * 5.00).setScale(2, RoundingMode.HALF_UP);
            }

            // Update daily change % cumulatively
            double finalChangePercent = stock.getChangePercent() + (percentChange / 2.0); // slow down cumulative percent representation
            if (finalChangePercent > 10.0) finalChangePercent = 10.0;
            if (finalChangePercent < -10.0) finalChangePercent = -10.0;

            stock.setCurrentPrice(newPrice);
            stock.setChangePercent(Math.round(finalChangePercent * 100.0) / 100.0);
            stockRepository.save(stock);

            // Evaluate auto trading rules for this price update
            autoTradeEngine.evaluateRules(stock);

            // Add to websocket broadcast payload
            sb.append(String.format(
                "{\"symbol\":\"%s\",\"price\":%s,\"changePercent\":%.2f}",
                stock.getSymbol(), newPrice.toString(), stock.getChangePercent()
            ));
            
            if (i < stocks.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]}");

        // Broadcast prices to all listening websocket sessions
        webSocketHandler.broadcast(sb.toString());
    }
}
