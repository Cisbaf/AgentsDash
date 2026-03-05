package com.painelagentesback.service;

import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.enitity.CallDetail;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AgentStatusService {
    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<>();
    private final GlobalMetricsService globalMetricsService;
    private final AgentDailyStatsRepository agentStatsRepository;


    public void updateAgentStatus(AgentsApi dto) {
        if (dto.getStatus() == 0) {
            return;
        }
        AgentStatus agent = agents.computeIfAbsent(dto.getId(), k -> new AgentStatus());

        synchronized (agent) {
            int previousStatusRamal = agent.getUltimoStatusRamal();
            int currentStatusRamal = dto.getSTATUS_();
            int previousStatusAcd = agent.getUltimoStatusAcd();
            int currentStatusAcd = dto.getStatus();

            agent.setId(dto.getId());
            agent.setNomeAgente(dto.getNAgente());

            // Atualiza métricas de tempo apenas se o agente estiver logado (status ACD = 1)
            if (currentStatusAcd == 1) {
                // Tempo em ligação (ramal = 1)
                if (currentStatusRamal == 1) {
                    agent.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos() + 1);
                }
                // Tempo livre (ramal = 0)
                if (currentStatusRamal == 0) {
                    agent.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos() + 1);
                }

                // Lógica para tempo de toque (ramal = 8)
                if (currentStatusRamal == 8 && previousStatusRamal != 8) {
                    agent.setRingingStartTime(LocalDateTime.now());
                } else if (currentStatusRamal == 1 && previousStatusRamal == 8) {
                    if (agent.getRingingStartTime() != null) {
                        long ringingDuration = Duration.between(agent.getRingingStartTime(), LocalDateTime.now()).getSeconds();
                        agent.setTempoTotalToqueSegundos(agent.getTempoTotalToqueSegundos() + ringingDuration);
                        globalMetricsService.addTotalToqueSegundos(ringingDuration);
                        agent.setRingingStartTime(null);
                    }
                } else if (currentStatusRamal != 8 && previousStatusRamal == 8) {
                    agent.setRingingStartTime(null);   // parou de tocar sem atender
                }

                // Registro de chamadas quando entra em ligação
                if (currentStatusRamal == 1 && previousStatusRamal != 1) {
                    String callerId = dto.getCallerIdRAni();
                    if (callerId != null && !callerId.isEmpty()) {
                        globalMetricsService.addCallDetails(callerId, LocalDateTime.now());
                        agent.getLigacoes().add(new CallDetail(callerId, LocalDateTime.now()));
                    }
                }
            }
            // Tempo em pausa (ACD = 5)
            if (currentStatusAcd == 5) {
                agent.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos() + 1);
            }
            if (currentStatusAcd == 6) {
                agent.setRemovido(agent.getRemovido() + 1);
            }
            // Contagem de pausas iniciadas
            if (currentStatusAcd == 5 && previousStatusAcd != 5) {
                agent.setPausasIniciadasTotal(agent.getPausasIniciadasTotal() + 1);
            }

            // Chamadas atendidas totais (vindo do campo n_ch_acd)
            agent.setChamadasAtendidasTotal(dto.getNChAcd());

            // Atualiza últimos status
            agent.setUltimoStatusAcd(currentStatusAcd);
            agent.setUltimoStatusRamal(currentStatusRamal);
        }
    }

    public Map<String, AgentStatus> getAllAgentStatuses() {
        return Map.copyOf(agents);
    }

    // Persiste o estado atual de todos os agentes no banco
    @Transactional
    public void persistCurrentState() {
        LocalDate today = LocalDate.now();
        for (AgentStatus agent : agents.values()) {
            AgentDailyStats stats = agentStatsRepository
                    .findByAgentIdAndDate(agent.getId(), today)
                    .orElse(new AgentDailyStats());
            if (agent.getUltimoStatusAcd() != 0) {
                stats.setAgentId(agent.getId());
                stats.setAgentName(agent.getNomeAgente());
                stats.setDate(today);
                stats.setChamadasAtendidasTotal(agent.getChamadasAtendidasTotal());
                stats.setPausasIniciadasTotal(agent.getPausasIniciadasTotal());
                stats.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos());
                stats.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos());
                stats.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos());
                stats.setTempoTotalToqueSegundos(agent.getTempoTotalToqueSegundos());
                stats.setUltimaAtualizacao(LocalDateTime.now());
                agentStatsRepository.save(stats);
            }
        }
    }

    // Carrega do banco os dados do dia atual para a memória (usado na inicialização)
    @Transactional
    public void loadFromDatabase() {
        LocalDate today = LocalDate.now();
        List<AgentDailyStats> todayStats = agentStatsRepository.findByDate(today);
        for (AgentDailyStats stats : todayStats) {
            AgentStatus agent = getAgentStatus(stats);
            agents.put(agent.getId(), agent);
        }
    }

    private static @NonNull AgentStatus getAgentStatus(AgentDailyStats stats) {
        AgentStatus agent = new AgentStatus();
        agent.setId(stats.getAgentId());
        agent.setNomeAgente(stats.getAgentName());
        agent.setChamadasAtendidasTotal(stats.getChamadasAtendidasTotal());
        agent.setPausasIniciadasTotal(stats.getPausasIniciadasTotal());
        agent.setTempoTotalPausaSegundos(stats.getTempoTotalPausaSegundos());
        agent.setTempoTotalLigacaoSegundos(stats.getTempoTotalLigacaoSegundos());
        agent.setTempoTotalLivreSegundos(stats.getTempoTotalLivreSegundos());
        agent.setTempoTotalToqueSegundos(stats.getTempoTotalToqueSegundos());
        return agent;
    }

    // Reseta os agentes em memória (usado no reset diário)
    public void resetAllAgentStatuses() {
        agents.clear();
        // Opcional: também poderia marcar como removido, mas como limpamos, ok
    }
}