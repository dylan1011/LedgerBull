package com.ledgerbull.position;

import com.ledgerbull.position.config.ExecutionProperties;
import com.ledgerbull.position.config.RiskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ExecutionProperties.class, RiskProperties.class})
public class PositionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PositionServiceApplication.class, args);
	}
}
