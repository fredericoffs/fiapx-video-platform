package com.fiapx.videoapi;

import org.springframework.boot.SpringApplication;

public class TestVideoApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(VideoApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
