package com.painelagentesback.controller;

import com.painelagentesback.service.AgentStatusService;
import com.painelagentesback.service.clients.AgentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentApiController {
    private final AgentClient agentClient;
    private final AgentStatusService agentStatusService;

    @GetMapping("/all-agents")
    ResponseEntity<?> getAllAgents() {
        return ResponseEntity.ok(agentClient.statusAgenteSystem());
    }
    @GetMapping("/pauses/{idAgents}")
    public ResponseEntity<?> getPauses(@PathVariable String idAgents) {
        return ResponseEntity.ok(agentStatusService.getPausas(idAgents));
    }
}
