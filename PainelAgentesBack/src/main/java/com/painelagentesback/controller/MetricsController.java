package com.painelagentesback.controller;

import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.GlobalMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final AgentStatusService agentStatusService;
    private final GlobalMetricsService globalMetricsService;
    private final AgentDailyStatsRepository agentStatsRepository;
    private final GlobalDailyStatsRepository globalStatsRepository;

    // Construtor

    @GetMapping("/agents/live")
    public Map<String, AgentStatus> getLiveAgentMetrics() {
        // Retorna o estado em memória da instância atual (pode ser líder ou não)
        return agentStatusService.getAllAgentStatuses();
    }

    @GetMapping("/agents")
    public List<AgentDailyStats> getAgentMetricsFromDB(@RequestParam(required = false) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return agentStatsRepository.findByDate(target);
    }

    @GetMapping("/global/live")
    public GlobalMetrics getLiveGlobalMetrics() {
        return globalMetricsService.getGlobalMetrics();
    }

    @GetMapping("/global")
    public Optional<GlobalDailyStats> getGlobalMetricsFromDB(@RequestParam(required = false) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return globalStatsRepository.findByDate(target);
    }
}
