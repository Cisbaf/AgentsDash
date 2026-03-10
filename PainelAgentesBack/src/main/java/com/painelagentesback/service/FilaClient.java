package com.painelagentesback.service;

import com.painelagentesback.models.utils.FilaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Service
@FeignClient(url = "http://192.168.1.52:7770/webservice/", name = "FilaClient")
public interface FilaClient {
    @GetMapping("status_fila_system/")
    FilaResponse statusFilaSystem();
}
