package com.painelagentesback.config;

import com.painelagentesback.service.AgentClient;
import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.GlobalMetricsService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
            System.err.println("Erro na coleta: " + e.getMessage());
        }
    }

    // Persistência a cada minuto
    @Scheduled(fixedRate = 10000)
    @SchedulerLock(name = "persistMetrics", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2M")
    public void persistMetrics() {
        agentStatusService.persistCurrentState();
        globalMetricsService.persistCurrentState();
    }

    // Reset diário à meia-noite
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "dailyReset", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void dailyReset() {
        // Persiste o último estado antes de resetar
        agentStatusService.persistCurrentState();
        globalMetricsService.persistCurrentState();

        agentStatusService.resetAllAgentStatuses();
        globalMetricsService.resetGlobalMetrics();
    }
}