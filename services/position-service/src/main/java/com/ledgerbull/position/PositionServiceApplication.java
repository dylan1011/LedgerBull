package com.ledgerbull.position;

import com.ledgerbull.position.config.ExecutionProperties;
import com.ledgerbull.position.config.RiskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({ExecutionProperties.class, RiskProperties.class})
@EnableScheduling
public class PositionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PositionServiceApplication.class, args);
	}
}
