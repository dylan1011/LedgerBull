package com.ledgerbull.execution;

import com.ledgerbull.execution.config.MatchingEngineProperties;
import com.ledgerbull.execution.config.RiskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MatchingEngineProperties.class, RiskProperties.class})
public class ExecutionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExecutionServiceApplication.class, args);
	}
}
