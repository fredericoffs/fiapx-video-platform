package com.fiapx.videogateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VideoGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(VideoGatewayApplication.class, args);
	}

}
