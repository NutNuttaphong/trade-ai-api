package com.trade.stocks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "auto_trade_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoTradeRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String type; // BUY or SELL

    @Column(nullable = false)
    private BigDecimal triggerPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status; // ACTIVE, EXECUTED, CANCELLED, FAILED
}
