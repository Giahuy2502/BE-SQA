package com.doan2025.webtoeic;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("UTC-SMOKE-001: Smoke test phụ thuộc DB/JPA; tắt để không làm fail bộ unit test.")
class WebtoeicApplicationTests {

	@Test
	void contextLoads() {
	}

}
