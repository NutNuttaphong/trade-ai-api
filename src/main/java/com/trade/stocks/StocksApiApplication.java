package com.trade.stocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StocksApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(StocksApiApplication.class, args);
	}
}
