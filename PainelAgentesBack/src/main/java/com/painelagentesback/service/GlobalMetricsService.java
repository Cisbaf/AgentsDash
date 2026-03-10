package com.painelagentesback.service;

import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.FilaResponse;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalMetricsService {
    @Getter
    private final GlobalMetrics globalMetrics = new GlobalMetrics();
    private final GlobalDailyStatsRepository globalStatsRepository;
    private final FilaClient filaClient;
    private final Set<String> uidsAnterioresNaFila = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> atendimentosRecentes = new ConcurrentHashMap<>();

    public void addCallDetails(String callerIdRAni, LocalDateTime timestamp) {
        globalMetrics.addCallDetails(callerIdRAni, timestamp);
        globalMetrics.incrementTotalChamadas();
        globalMetrics.setTotalChamadasAtendidas(globalMetrics.getAllCallDetails().size());
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

        globalStatsRepository.save(stats);


        stats.setTotalChamadasAtendidas(globalMetrics.getAllCallDetails().size());

        // Após persistir, podemos limpar a lista em memória se quisermos,
        // mas como mantemos para first/last, talvez não.
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
            // Carregar detalhes das chamadas? Talvez não seja necessário se first/last forem consultados do banco
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
    }

    // Métdo para ser chamado pelo AgentStatusService ao atender
    public void registrarAtendimento(String uid) {
        if (uid != null) {
            atendimentosRecentes.put(uid, LocalDateTime.now());
        }
    }

    // Métdo para o AgentStatusService verificar se a chamada continua na fila
    public boolean estaNaFila(String uid) {
        return uidsAnterioresNaFila.contains(uid);
    }
}

