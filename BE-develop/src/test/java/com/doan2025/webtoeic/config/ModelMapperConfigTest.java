package com.doan2025.webtoeic.config;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelMapperConfigTest {

    // UTC-MM-001: Smoke test bean ModelMapper không null và có matching strategy STANDARD
    @Test
    void modelMapper_shouldNotBeNull_andUseStandardStrategy() {
        // Given
        ModelMapperConfig config = new ModelMapperConfig();

        // When
        ModelMapper mapper = config.modelMapper();

        // Then
        assertNotNull(mapper);
        assertEquals(MatchingStrategies.STANDARD, mapper.getConfiguration().getMatchingStrategy());
    }
}

