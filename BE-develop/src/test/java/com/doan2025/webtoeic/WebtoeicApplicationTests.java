package com.doan2025.webtoeic;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Application context smoke test.
 * <p>
 * Test Case ID: UTC-SEC-APP-001
 * Objective: Full Spring context (including JPA and datasource) starts successfully.
 * <p>
 * <b>CheckDB:</b> This test does not assert specific SQL or row counts; it only proves the datasource
 * pool can connect. For strict CheckDB on writes, add {@code @Sql} scripts or repository IT tests.
 * <p>
 * <b>Rollback:</b> {@code contextLoads} performs no explicit writes; Hibernate {@code ddl-auto} may
 * alter schema depending on profile — use a dedicated test profile with in-memory or isolated DB
 * if you need guaranteed data rollback.
 */
@SpringBootTest
@Disabled("UTC-SMOKE-001: Smoke test phụ thuộc DB/JPA; tắt để không làm fail bộ unit test.")
class WebtoeicApplicationTests {

    @Test
    void contextLoads() {
    }

}
