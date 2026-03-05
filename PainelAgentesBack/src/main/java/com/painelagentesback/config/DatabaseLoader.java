package com.painelagentesback.config;

import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.GlobalMetricsService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class DatabaseLoader {

    private final AgentStatusService agentStatusService;
    private final GlobalMetricsService globalMetricsService;

    public DatabaseLoader(AgentStatusService agentStatusService,
                          GlobalMetricsService globalMetricsService) {
        this.agentStatusService = agentStatusService;
        this.globalMetricsService = globalMetricsService;
    }

    @PostConstruct
    public void load() {
        agentStatusService.loadFromDatabase();
        globalMetricsService.loadFromDatabase();
    }
}