package org.dragon.yunpeng.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class,
		TransactionAutoConfiguration.class })
@ImportResource({ "classpath:applicationContext.xml", "classpath:security-context.xml" })
public class LegacySpringAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(LegacySpringAppApplication.class, args);
	}
}
