package com.painelagentesback.service;

import com.painelagentesback.models.enitity.CallDetail;
import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.CallDetailRepository;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GlobalMetricsService {
    @Getter
    private final GlobalMetrics globalMetrics = new GlobalMetrics();
    private final GlobalDailyStatsRepository globalStatsRepository;
    private final CallDetailRepository callDetailRepository;


    public void addCallDetails(String callerIdRAni, LocalDateTime timestamp) {
        globalMetrics.addCallDetails(callerIdRAni, timestamp);
        globalMetrics.incrementTotalChamadas();
    }

    public void addTotalToqueSegundos(long seconds) {
        globalMetrics.addTotalToqueSegundos(seconds);
    }

    // Persiste o estado global no banco
    @Transactional
    public void persistCurrentState() {
        LocalDate today = LocalDate.now();
        GlobalDailyStats stats = globalStatsRepository
                .findByDate(today)
                .orElse(new GlobalDailyStats());
        stats.setDate(today);
        stats.setTotalChamadas(globalMetrics.getTotalChamadas());
        stats.setChamadasAtendidas(globalMetrics.getChamadasAtendidas());
        stats.setChamadasAbandonadas(globalMetrics.getChamadasAbandonadas());
        stats.setChamadasEmFila(globalMetrics.getChamadasEmFila());
        stats.setTempoTotalToqueSegundosGlobal(globalMetrics.getTempoTotalToqueSegundosGlobal());
        stats.setUltimaAtualizacao(LocalDateTime.now());
        globalStatsRepository.save(stats);

        // Persistir detalhes das chamadas (se necessário)
        for (CallDetail call : globalMetrics.getAllCallDetails()) {
            // Evitar duplicatas: pode-se verificar se já existe
            CallDetail detail = new CallDetail();
            detail.setCallerIdRAni(call.getCallerIdRAni());
            detail.setTimestamp(call.getTimestamp());
            detail.setDate(today);
            callDetailRepository.save(detail);
        }
        // Após persistir, podemos limpar a lista em memória se quisermos,
        // mas como mantemos para first/last, talvez não.
    }

    // Carrega do banco os dados globais do dia
    @Transactional
    public void loadFromDatabase() {
        LocalDate today = LocalDate.now();
        Optional<GlobalDailyStats> opt = globalStatsRepository.findByDate(today);
        opt.ifPresent(stats -> {
            globalMetrics.setTotalChamadas(stats.getTotalChamadas());
            globalMetrics.setChamadasAtendidas(stats.getChamadasAtendidas());
            globalMetrics.setChamadasAbandonadas(stats.getChamadasAbandonadas());
            globalMetrics.setChamadasEmFila(stats.getChamadasEmFila());
            globalMetrics.setTempoTotalToqueSegundosGlobal(stats.getTempoTotalToqueSegundosGlobal());
            // Carregar detalhes das chamadas? Talvez não seja necessário se first/last forem consultados do banco
        });

        // Se quisermos carregar os detalhes das chamadas para ter first/last em memória,
        // podemos buscar as do dia e preencher a lista.
        List<CallDetail> calls = callDetailRepository.findByDateOrderByTimestampAsc(today);
        for (CallDetail call : calls) {
            globalMetrics.addCallDetails(call.getCallerIdRAni(), call.getTimestamp());
        }
    }

    public void resetGlobalMetrics() {
        globalMetrics.setTotalChamadas(0);
        globalMetrics.setChamadasAtendidas(0);
        globalMetrics.setChamadasAbandonadas(0);
        globalMetrics.setChamadasEmFila(0);
        globalMetrics.setTempoTotalToqueSegundosGlobal(0);
        globalMetrics.getAllCallDetails().clear();
    }
}

