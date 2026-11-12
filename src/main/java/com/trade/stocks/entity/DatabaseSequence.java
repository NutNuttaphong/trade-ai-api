package com.trade.stocks.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Counter document used to generate sequential numeric IDs when running on MongoDB.
 */
@Document(collection = "database_sequences")
@Data
public class DatabaseSequence {
    @Id
    private String id;
    private long seq;
}
