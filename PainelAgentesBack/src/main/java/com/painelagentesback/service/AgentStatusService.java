package com.painelagentesback.service;

import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.enitity.CallDetail;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusService {
    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<>();
    private final GlobalMetricsService globalMetricsService;
    private final AgentDailyStatsRepository agentStatsRepository;

    public void updateAgentStatus(AgentsApi dto) {
        if (dto.getStatus() == 0) return;

        AgentStatus agent = agents.computeIfAbsent(dto.getId(), k -> new AgentStatus());

        synchronized (agent) {
            processStatusUpdate(agent, dto);
        }
    }

    private void processStatusUpdate(AgentStatus agent, AgentsApi dto) {
        // Captura estados para comparação
        int prevRamal = agent.getUltimoStatusRamal();
        int currRamal = dto.getSTATUS_();
        int prevAcd = agent.getUltimoStatusAcd();
        int currAcd = dto.getStatus();
        String callerId = dto.getCallerIdRAni();

        globalMetricsService.contarChamadas();

        // Atualização básica de perfil
        agent.setId(dto.getId());
        agent.setNomeAgente(dto.getNAgente());
        agent.setAgentRole(defineAgentRole(dto.getId()));
        agent.setChamadasRecebidasTotal(dto.getNChAcd());

        // Processamento de Lógicas Específicas
        contabilizarTemposContinuos(agent, currAcd, currRamal);
        processarLogicaToque(agent, prevRamal, currRamal, callerId);
        contabilizarContadoresEvento(agent, prevAcd, currAcd);

        // Persistência de estado para próxima iteração
        agent.setUltimoStatusAcd(currAcd);
        agent.setUltimoStatusRamal(currRamal);

        atualizarMetricasGlobais();
    }

    /**
     * Calcula o incremento de tempo (1s) para os estados de Ligação, Livre ou Pausa.
     */
    private void contabilizarTemposContinuos(AgentStatus agent, int currAcd, int currRamal) {
        if (currAcd == 1) { // Agente Logado/Ativo
            if (currRamal == 1) agent.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos() + 1);
            else if (currRamal == 0) agent.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos() + 1);
        } else if (currAcd == 5) { // Agente em Pausa
            agent.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos() + 1);
        }
    }

    /**
     * Gerencia o ciclo de vida do toque (Ringing).
     * Identifica início, fim, atendimento ou remoção da chamada.
     */
    private void processarLogicaToque(AgentStatus agent, int prevRamal, int currRamal, String callerId) {
        boolean iniciouToque = (currRamal == 8 && prevRamal != 8);
        boolean parouToque = (prevRamal == 8 && currRamal != 8);

        if (iniciouToque) {
            agent.setRingingStartTime(LocalDateTime.now());
            log.debug("[TRACK] Chamada {} começou a tocar para {}", callerId, agent.getNomeAgente());
        } else if (parouToque) {
            finalizarCicloToque(agent, currRamal, callerId);
        }
    }

    private void finalizarCicloToque(AgentStatus agent, int currRamal, String callerId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Contabiliza tempo de toque
        if (agent.getRingingStartTime() != null) {
            long duration = Duration.between(agent.getRingingStartTime(), now).getSeconds();
            agent.setTempoTotalToqueSegundos(agent.getTempoTotalToqueSegundos() + duration);
            globalMetricsService.addTotalToqueSegundos(duration);
        }

        // 2. Determina o desfecho da chamada para o agente
        if (callerId != null && !callerId.isEmpty()) {
            if (currRamal == 1) { // Atendeu
                globalMetricsService.registrarAtendimento(callerId);
                globalMetricsService.addCallDetails(callerId, now);
                agent.getLigacoes().add(new CallDetail(callerId, now));
                log.debug("[TRACK-ATENDIMENTO] {} atendeu {}", agent.getNomeAgente(), callerId);
            } else if (globalMetricsService.estaNaFila(callerId)) { // Removido (foi para outro agente)
                agent.setRemovido(agent.getRemovido() + 1);
                log.info("[TRACK-REMOVIDO] {} perdeu a chamada {}. Segue na fila.", agent.getNomeAgente(), callerId);
            } else {
                globalMetricsService.incrementChamadasAbandonadas();
                log.info("[TRACK-ABANDONADA] {} perdeu a chamada {}. Saiu da fila.", agent.getNomeAgente(), callerId);
            }
        }
        agent.setRingingStartTime(null);
    }

    private void contabilizarContadoresEvento(AgentStatus agent, int prevAcd, int currAcd) {
        if (currAcd == 5 && prevAcd != 5) {
            agent.setPausasIniciadasTotal(agent.getPausasIniciadasTotal() + 1);
        }
    }

    private String defineAgentRole(String id) {
        if (id == null || id.length() <= 11) return "MEDICO";
        if (id.startsWith("1")) return "TARM";
        if (id.startsWith("2")) return "FROTA";
        return "OUTRO";
    }

    private void atualizarMetricasGlobais() {
        long total = agents.values().stream()
                .mapToLong(AgentStatus::getChamadasRecebidasTotal)
                .sum();
        globalMetricsService.addChamadasRecebidas(total);
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
                stats.setChamadasRecebidasTotal(agent.getChamadasRecebidasTotal());
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
        agent.setChamadasRecebidasTotal(stats.getChamadasRecebidasTotal());
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