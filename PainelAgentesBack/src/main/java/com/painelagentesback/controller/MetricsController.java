package com.painelagentesback.controller;

import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.clients.FilaClient;
import com.painelagentesback.service.GlobalMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final FilaClient filaClient;

    @GetMapping("/fila")
    public ResponseEntity<?> fila() {
        return ResponseEntity.ok(filaClient.statusFilaSystem());
    }

    @GetMapping("/agents/live")
    public ResponseEntity<Map<String, AgentStatus>> getLiveAgentMetrics() {
        try {
            return ResponseEntity.ok(agentStatusService.getAllAgentStatuses());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/agents")
    public ResponseEntity<List<AgentDailyStats>> getAgentMetricsFromDB(@RequestParam(required = false) LocalDate date) {
        try {
            LocalDate target = date != null ? date : LocalDate.now();
            var agents = agentStatsRepository.findByDate(target);
            if (agents.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(agents);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping("/global/live")
    public ResponseEntity<GlobalMetrics> getLiveGlobalMetrics() {
        try {
            var global = globalMetricsService.getGlobalMetrics();
            if (global == null || global.getAllCallDetails().isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(global);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/global")
    public ResponseEntity<Optional<GlobalDailyStats>> getGlobalMetricsFromDB(@RequestParam(required = false) LocalDate date) {
        try {
            LocalDate target = date != null ? date : LocalDate.now();
            var global = globalStatsRepository.findByDate(target);
            if (global.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(global);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
