package com.fiapx.videoworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VideoWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(VideoWorkerApplication.class, args);
	}

}
