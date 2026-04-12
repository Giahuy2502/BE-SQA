package com.doan2025.webtoeic.config;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TCID nhóm: UTC-CC-*
 * Smoke test bean Cloudinary từ {@link CloudConfig}.
 */
class CloudConfigTest {

    /**
     * TCID: UTC-CC-001
     * cloudinary() trả về instance Cloudinary khi inject giá trị @Value (ReflectionTestUtils).
     */
    @Test
    void cloudinary_shouldReturnNonNullBean_whenPropertiesSet() {
        CloudConfig config = new CloudConfig();
        ReflectionTestUtils.setField(config, "CLOUD_NAME", "demo-cloud");
        ReflectionTestUtils.setField(config, "API_KEY", "api-key-test");
        ReflectionTestUtils.setField(config, "API_SECRET", "api-secret-test");

        Cloudinary cloudinary = config.cloudinary();

        assertNotNull(cloudinary);
    }
}
