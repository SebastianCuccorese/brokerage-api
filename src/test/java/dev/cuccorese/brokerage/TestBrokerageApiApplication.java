package dev.cuccorese.brokerage;

import org.springframework.boot.SpringApplication;

public class TestBrokerageApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(BrokerageApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
