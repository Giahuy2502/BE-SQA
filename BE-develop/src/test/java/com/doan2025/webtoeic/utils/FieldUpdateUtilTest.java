package com.doan2025.webtoeic.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TCID nhóm: UTC-FUU-*
 * Kiểm thử {@link FieldUpdateUtil} — helper cập nhật field có điều kiện.
 */
class FieldUpdateUtilTest {

    /**
     * TCID: UTC-FUU-001
     * Khi giá trị hiện tại null và giá trị mới khác null thì setter được gọi.
     */
    @Test
    void updateIfNeeded_shouldSet_whenCurrentNullAndNewNonNull() {
        AtomicReference<String> holder = new AtomicReference<>(null);
        FieldUpdateUtil<String> util = new FieldUpdateUtil<>(holder::get, holder::set, "newValue");

        util.updateIfNeeded();

        assertEquals("newValue", holder.get());
    }

    /**
     * TCID: UTC-FUU-002
     * Khi giá trị hiện tại khác giá trị mới thì cập nhật.
     */
    @Test
    void updateIfNeeded_shouldSet_whenValuesDiffer() {
        AtomicReference<String> holder = new AtomicReference<>("old");
        FieldUpdateUtil<String> util = new FieldUpdateUtil<>(holder::get, holder::set, "fresh");

        util.updateIfNeeded();

        assertEquals("fresh", holder.get());
    }

    /**
     * TCID: UTC-FUU-003a
     * Cả current và new đều null — không cập nhật.
     */
    @Test
    void updateIfNeeded_shouldNotSet_whenBothNull() {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> holder = new AtomicReference<>(null);
        FieldUpdateUtil<String> util = new FieldUpdateUtil<>(
                holder::get,
                v -> {
                    callCount.incrementAndGet();
                    holder.set(v);
                },
                null
        );
        util.updateIfNeeded();
        assertEquals(0, callCount.get());
        assertNull(holder.get());
    }

    /**
     * TCID: UTC-FUU-003
     * Khi giá trị hiện tại bằng giá trị mới thì không thay đổi (không gọi setter với logic equals).
     */
    @Test
    void updateIfNeeded_shouldNotChange_whenValuesEqual() {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> holder = new AtomicReference<>("same");
        FieldUpdateUtil<String> util = new FieldUpdateUtil<>(
                holder::get,
                v -> {
                    callCount.incrementAndGet();
                    holder.set(v);
                },
                "same"
        );

        util.updateIfNeeded();

        assertEquals(0, callCount.get());
        assertEquals("same", holder.get());
    }

    /**
     * TCID: UTC-FUU-004
     * getCurrentValue trả về đúng getter.
     */
    @Test
    void getCurrentValue_shouldReturnGetterValue() {
        FieldUpdateUtil<String> util = new FieldUpdateUtil<>(() -> "x", s -> {}, "y");
        assertEquals("x", util.getCurrentValue());
    }

    /**
     * TCID: UTC-FUU-005
     * getNewValue / getSetter từ Lombok hoạt động.
     */
    @Test
    void getters_shouldExposeConstructorArgs() {
        FieldUpdateUtil<Integer> util = new FieldUpdateUtil<>(() -> 1, i -> {}, 42);
        assertEquals(42, util.getNewValue());
        assertNotNull(util.getSetter());
    }
}
