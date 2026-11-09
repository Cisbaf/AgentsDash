package com.painelagentesback.service;

import com.painelagentesback.repository.GlobalDailyStatsRepository;
import com.painelagentesback.service.clients.FilaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GlobalMetricsServiceTest {

    private static final String CALLER = "11987654321";

    private GlobalMetricsService service;

    @BeforeEach
    void setUp() {
        service = new GlobalMetricsService(mock(GlobalDailyStatsRepository.class), mock(FilaClient.class));
    }

    @Test
    @DisplayName("wasAttendedAfter ignora o agente excluído")
    void shouldIgnoreExcludedAgent() {
        LocalDateTime toque = LocalDateTime.now().minusSeconds(30);
        service.registrarAtendimentoConfirmado(CALLER, "A", LocalDateTime.now());

        assertTrue(service.wasAttendedAfter(CALLER, toque));
        assertFalse(service.wasAttendedAfter(CALLER, toque, "A"));
        assertTrue(service.wasAttendedAfter(CALLER, toque, "B"));
    }

    @Test
    @DisplayName("Confirmação de outro agente não é apagada por confirmação posterior do excluído")
    void shouldKeepOtherAgentConfirmation() {
        LocalDateTime toque = LocalDateTime.now().minusSeconds(30);
        service.registrarAtendimentoConfirmado(CALLER, "B", LocalDateTime.now().minusSeconds(10));
        service.registrarAtendimentoConfirmado(CALLER, "A", LocalDateTime.now());

        assertTrue(service.wasAttendedAfter(CALLER, toque, "A"));
    }

    @Test
    @DisplayName("Ignora confirmações anteriores ao toque")
    void shouldIgnoreConfirmationBeforeRing() {
        service.registrarAtendimentoConfirmado(CALLER, "B", LocalDateTime.now().minusMinutes(1));

        assertFalse(service.wasAttendedAfter(CALLER, LocalDateTime.now().minusSeconds(30), "A"));
    }
}
