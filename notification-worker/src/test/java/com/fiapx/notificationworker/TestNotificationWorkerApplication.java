package com.fiapx.notificationworker;

import org.springframework.boot.SpringApplication;

public class TestNotificationWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(NotificationWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
