package com.painelagentesback.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.models.utils.FilaResponse;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
import com.painelagentesback.service.clients.FilaClient;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalMetricsService {
    @Getter
    private final GlobalMetrics globalMetrics = new GlobalMetrics();
    private final GlobalDailyStatsRepository globalStatsRepository;
    private final FilaClient filaClient;


    private final Cache<String, LocalDateTime> attendedCalls = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)  // limite seguro para evitar crescimento descontrolado
            .recordStats()         // opcional: para monitoramento
            .build();

    private final Cache<String, LocalDateTime> confirmedAttendances = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    // Cache para rastreamento global de chamadas (última vez vista)
    private final Cache<String, LocalDateTime> globalCallTracker = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(20_000)
            .build();

    public void addCallDetails(String callerIdRAni, LocalDateTime timestamp) {
        globalMetrics.addCallDetails(callerIdRAni, timestamp);
    }

    public void incrementTotalChamadas(Collection<AgentStatus> recebidas) {
        globalMetrics.setTotalchamadasRecebidas(0);
        for(AgentStatus agentStatus:recebidas) {
            globalMetrics.setTotalchamadasRecebidas(agentStatus.getChamadasRecebidasTotal() + globalMetrics.getTotalchamadasRecebidas());
        }
        var total = globalMetrics.getTotalchamadasRecebidas();
        globalMetrics.setTotalChamadasAtendidas(total - globalMetrics.getChamadasAbandonadas());
    }

    public void registrarAtendimentoConfirmado(String callerId, LocalDateTime answeredAt) {
        if (callerId != null && !callerId.isEmpty()) {
            confirmedAttendances.put(callerId, answeredAt);
            log.debug("Chamada {} confirmada como atendimento real em {}", callerId, answeredAt);
        }
    }


    public void addTotalToqueSegundos(long seconds) {
        globalMetrics.addTotalToqueSegundos(seconds);
    }

    public synchronized void incrementChamadasAbandonadas() {
        globalMetrics.setChamadasAbandonadas(globalMetrics.getChamadasAbandonadas() + 1);
    }

    public void addChamadasRecebidas(long chamada) {
        globalMetrics.setTotalchamadasRecebidas(chamada);
    }

    void contarChamadas() {
        FilaResponse response = filaClient.statusFilaSystem();
        var size = 0;
        if (response != null && response.getChamadas() != null && response.getChamadas().getItens() != null) {
            size = response.getChamadas().getItens().size();
        }
        globalMetrics.setChamadasEmFila(size);
    }

    // Persiste o estado global no banco
    @Transactional
    public void persistCurrentState() {
        LocalDate today = LocalDate.now();
        GlobalDailyStats stats = globalStatsRepository
                .findByDate(today)
                .orElse(new GlobalDailyStats());
        stats.setDate(today);
        stats.setTotalchamadasRecebidas(globalMetrics.getTotalchamadasRecebidas());
        stats.setChamadasAbandonadas(globalMetrics.getChamadasAbandonadas());
        stats.setChamadasEmFila(globalMetrics.getChamadasEmFila());
        stats.setTempoTotalToqueSegundosGlobal(globalMetrics.getTempoTotalToqueSegundosGlobal());
        stats.setUltimaAtualizacao(LocalDateTime.now());
        stats.setTotalChamadasAtendidas(globalMetrics.getTotalChamadasAtendidas());

        globalStatsRepository.save(stats);
    }

    // Carrega do banco os dados globais do dia
    @Transactional
    public void loadFromDatabase() {
        LocalDate today = LocalDate.now();
        Optional<GlobalDailyStats> opt = globalStatsRepository.findByDate(today);
        opt.ifPresent(stats -> {
            globalMetrics.setTotalchamadasRecebidas(stats.getTotalchamadasRecebidas());
            globalMetrics.setChamadasAbandonadas(stats.getChamadasAbandonadas());
            globalMetrics.setChamadasEmFila(stats.getChamadasEmFila());
            globalMetrics.setTempoTotalToqueSegundosGlobal(stats.getTempoTotalToqueSegundosGlobal());
            globalMetrics.setTotalChamadasAtendidas(stats.getTotalChamadasAtendidas());
        });
    }

    public void resetGlobalMetrics() {
        globalMetrics.setTotalChamadasAtendidas(0);
        globalMetrics.setTotalchamadasRecebidas(0);
        globalMetrics.setChamadasAbandonadas(0);
        globalMetrics.setChamadasEmFila(0);
        globalMetrics.setTempoTotalToqueSegundosGlobal(0);
        globalMetrics.clearCallDetails();
        globalStatsRepository.deleteAll();
    }

    public void registrarAtendimento(String callerId, LocalDateTime answeredAt) {
        if (callerId != null && !callerId.isEmpty()) {
            attendedCalls.put(callerId, answeredAt);
            log.debug("Chamada {} registrada como atendida em {}", callerId, answeredAt);
        }
    }

    // Verifica se uma chamada foi atendida após um determinado instante
    public boolean wasAttendedAfter(String callerId, LocalDateTime since) {
        LocalDateTime t = confirmedAttendances.getIfPresent(callerId);
        return t != null && t.isAfter(since);
    }

    // Adiciona um callerId ao rastreador global (quando toca ou é atendida)
    public void trackGlobalCall(String callerId, LocalDateTime timestamp) {
        if (callerId != null && !callerId.isEmpty()) {
            globalCallTracker.put(callerId, timestamp);
        }
    }

    // Campo "numero" bate com CallerIdRAni. Se for uid, troque item.getNumero() por item.getUid()
    public boolean estaCallIdNaFila(String callerId) {
        try {
            FilaResponse response = filaClient.statusFilaSystem();
            if (response == null
                    || response.getChamadas() == null
                    || response.getChamadas().getItens() == null) {
                return false;
            }
            return response.getChamadas().getItens().stream()
                    .anyMatch(item -> callerId.equals(item.getNumero()));
        } catch (Exception e) {
            log.warn("[FILA-CHECK] Erro ao consultar fila para callerId={}: {}", callerId, e.getMessage());
            return false;
        }
    }
}