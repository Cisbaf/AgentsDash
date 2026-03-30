package com.painelagentesback.service.clients;

import com.painelagentesback.models.api.ApiRequest;
import com.painelagentesback.models.api.EventDetails;
import com.painelagentesback.models.enitity.AgentsApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@Service
@FeignClient(url = "http://192.168.1.10:8013", name = "ConsultaClient")
public interface ConsultaClient {
    @PostMapping("/consult")
    Map<String, Map<String, EventDetails>> consult(@RequestBody ApiRequest apiRequest);
}
