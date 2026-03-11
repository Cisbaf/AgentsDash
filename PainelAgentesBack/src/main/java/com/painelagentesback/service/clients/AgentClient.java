package com.painelagentesback.service.clients;

import com.painelagentesback.models.enitity.AgentsApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Service
@FeignClient(url = "http://192.168.1.52:7770/webservice/", name = "AgentClient")
public interface AgentClient {
    @GetMapping("status_agente_system/type=/?token=c05aa23a72ef462a871eee84c5621ac1")
    List<AgentsApi> statusAgenteSystem();
}
