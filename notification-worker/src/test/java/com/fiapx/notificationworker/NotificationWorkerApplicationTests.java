package com.fiapx.notificationworker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationWorkerApplicationTests {

	@Test
	void contextLoads() {
	}

}
