package com.painelagentesback.controller;

import com.painelagentesback.service.clients.AgentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentApiController {
    private final AgentClient agentClient;

    @GetMapping("/all-agents")
    ResponseEntity<?> getAllAgents() {
        return ResponseEntity.ok(agentClient.statusAgenteSystem());
    }
}
