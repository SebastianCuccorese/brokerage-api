package dev.cuccorese.brokerage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BrokerageApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrokerageApiApplication.class, args);
	}

}
