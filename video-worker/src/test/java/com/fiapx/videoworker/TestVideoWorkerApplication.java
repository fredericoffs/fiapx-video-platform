package com.fiapx.videoworker;

import org.springframework.boot.SpringApplication;

public class TestVideoWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(VideoWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
