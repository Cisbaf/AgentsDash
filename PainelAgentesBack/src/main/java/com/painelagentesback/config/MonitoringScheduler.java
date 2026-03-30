package com.painelagentesback.config;

import com.painelagentesback.service.clients.AgentClient;
import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.GlobalMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoringScheduler {
    private final AgentClient agentStatusApiClient;
    private final AgentStatusService agentStatusService;
    private final GlobalMetricsService globalMetricsService;

    // Coleta a cada segundo - apenas a instância que obtiver o lock executa
    @Scheduled(fixedRate = 1000)
    @SchedulerLock(name = "collectAgentData", lockAtLeastFor = "PT1S", lockAtMostFor = "PT5S")
    public void collectAndProcessData() {
        try {
            var agentes = agentStatusApiClient.statusAgenteSystem();
            if (agentes != null) {
                for (var dto : agentes) {
                    agentStatusService.updateAgentStatus(dto);
                }
            }
        } catch (Exception e) {
            // Log de erro
            log.error("Erro na coleta: {}", e.getMessage(), e);
        }
    }

    // Coleta a cada 3 segundos
    @Scheduled(fixedRate = 3000)
    @SchedulerLock(name = "persistMetrics", lockAtLeastFor = "PT4S", lockAtMostFor = "PT10S")
    public void persistMetrics() {
        agentStatusService.persistCurrentState();
        globalMetricsService.persistCurrentState();
    }

    @Scheduled(cron = "0 0 7 * * *")
    @SchedulerLock(name = "dailyReset_morning", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void dailyResetMorning() {
        executarReset("7h");
    }

    @Scheduled(cron = "0 0 19 * * *")
    @SchedulerLock(name = "dailyReset_evening", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void dailyResetEvening() {
        executarReset("19h");
    }

    private void executarReset(String horario) {

        // Executa os resets
        agentStatusService.resetAllAgentStatuses();
        globalMetricsService.resetGlobalMetrics();

        log.warn("Reset programado às {} concluído com sucesso", horario);
    }
}