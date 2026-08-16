package com.fiapx.videoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VideoApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(VideoApiApplication.class, args);
	}

}
