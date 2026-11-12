package com.trade.stocks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Entity
@Table(name = "auto_trade_rules")
@Document(collection = "auto_trade_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoTradeRule {
    @Id
    @org.springframework.data.annotation.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Indexed
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String type; // BUY or SELL

    @Column(nullable = false)
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal triggerPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    @Indexed
    private String status; // ACTIVE, EXECUTED, CANCELLED, FAILED
}
