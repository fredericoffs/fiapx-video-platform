package com.fiapx.videoworker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VideoWorkerApplicationTests {

	@Test
	void contextLoads() {
	}

}
