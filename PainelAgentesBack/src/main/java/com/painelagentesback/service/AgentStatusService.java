package com.painelagentesback.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.enitity.CallDetail;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatusService {

    // ─── Dependências ────────────────────────────────────────────────────────────

    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<>();
    private final GlobalMetricsService globalMetricsService;
    private final AgentDailyStatsRepository agentStatsRepository;

    // ─── Scheduler e caches ──────────────────────────────────────────────────────

    private final ScheduledExecutorService scheduler =
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2);

    // Impede que o mesmo agente agende duas verificações para o mesmo callerId.
    // Chave: "callerId:agentId" ou "poscurto:callerId"
    private final Cache<String, Boolean> pendingCallChecks = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5)).maximumSize(10_000).build();

    // Garante que uma chamada não seja contada como abandonada duas vezes
    // (pode haver dois agentes verificando o mesmo callerId simultaneamente).
    private final Cache<String, Boolean> processedAsAbandoned = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5)).maximumSize(10_000).build();

    // Máximo de rechecks enquanto a chamada ainda está na fila (10s cada → 100s total)
    private static final int MAX_TENTATIVAS_FILA = 10;

    // ─── Entry point ─────────────────────────────────────────────────────────────

    public void updateAgentStatus(AgentsApi dto) {
        if (dto.getStatus() == 0) return;
        AgentStatus agent = agents.computeIfAbsent(dto.getId(), k -> new AgentStatus());
        synchronized (agent) {
            try {
                processStatusUpdate(agent, dto);
            } catch (Exception e) {
                log.error("Erro crítico ao processar agente {}: {}", agent.getNomeAgente(), e.getMessage(), e);
            }
        }
    }

    // ─── Processamento principal ─────────────────────────────────────────────────

    private void processStatusUpdate(AgentStatus agent, AgentsApi dto) {
        int prevRamal = agent.getUltimoStatusRamal();
        int currRamal = dto.getSTATUS_();
        int prevAcd = agent.getUltimoStatusAcd();
        int currAcd = dto.getStatus();
        String callerId = dto.getCallerIdRAni();
        agent.setAgentRole(defineAgentRole(dto.getId()));

        LocalDateTime now = LocalDateTime.now();
        if (callerId.length() < 8 || agent.getNomeAgente() == null) {
            return;
        }

        globalMetricsService.contarChamadas();
        agent.setId(dto.getId());
        agent.setNomeAgente(dto.getNAgente());
        agent.setChamadasRecebidasTotal(dto.getNChAcd());

        if (hasCallerId(callerId) && (currRamal == 8 || currRamal == 1))
            globalMetricsService.trackGlobalCall(callerId, now);

        try {
            acumularTempos(agent, currAcd, currRamal);
            processarToque(agent, prevRamal, currRamal, callerId, now);
            processarAtendimento(agent, currRamal, callerId, now);
            contarPausas(agent, prevAcd, currAcd);
        } catch (Exception e) {
            log.warn("Falha em métricas secundárias para {}: {}", agent.getNomeAgente(), e.getMessage());
        }

        agent.setUltimoStatusAcd(currAcd);
        agent.setUltimoStatusRamal(currRamal);
        atualizarMetricasGlobais();
    }

    // ─── Tempos contínuos ────────────────────────────────────────────────────────

    private void acumularTempos(AgentStatus agent, int acd, int ramal) {
        if (acd == 1) {
            if (ramal == 1) agent.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos() + 1);
            else if (ramal == 0) agent.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos() + 1);
        } else if (acd == 5) {
            agent.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos() + 1);
        }
    }

    private void contarPausas(AgentStatus agent, int prevAcd, int currAcd) {
        if (currAcd == 5 && prevAcd != 5)
            agent.setPausasIniciadasTotal(agent.getPausasIniciadasTotal() + 1);
    }

    // ─── Lógica de toque (ramal == 8) ────────────────────────────────────────────

    private void processarToque(AgentStatus agent, int prevRamal, int currRamal, String callerId, LocalDateTime now) {
        if (currRamal == 8 && prevRamal != 8) {
            // Começou a tocar
            agent.setRingingStartTime(now);
            agent.setLastAnsweredCallerId(callerId);
            log.info("[TOQUE] {} → {}", callerId, agent.getNomeAgente());

        } else if (prevRamal == 8 && currRamal != 8) {
            // Parou de tocar
            if (agent.getRingingStartTime() != null) {
                long dur = Duration.between(agent.getRingingStartTime(), now).getSeconds();
                agent.setTempoTotalToqueSegundos(agent.getTempoTotalToqueSegundos() + dur);
                globalMetricsService.addTotalToqueSegundos(dur);

                // Se não atendeu (currRamal != 1), agenda verificação de desfecho
                if (currRamal != 1) {
                    String id = resolveCallerId(callerId, agent);
                    if (hasCallerId(id)) agendarVerificacaoDesfecho(agent, id, now);
                }
            }
            agent.setRingingStartTime(null);
        }
    }

    // ─── Lógica de atendimento (ramal == 1) ──────────────────────────────────────
    //
    // Fluxos possíveis:
    //   ramal 8→1 (atendeu normalmente) → confirma após 15s
    //   ramal 8→1→0 em < 15s (curto)   → registra imediatamente, verifica abandono depois
    //   ramal 8→1→0 após 15s (repasse) → attendanceRegistered=true → limpa estado, sem "removido"

    private void processarAtendimento(AgentStatus agent, int currRamal, String callerId, LocalDateTime now) {
        if (currRamal == 1) {
            iniciarOuConfirmarAtendimento(agent, callerId, now);
        } else {
            encerrarAtendimento(agent, now);
        }
    }

    private void iniciarOuConfirmarAtendimento(AgentStatus agent, String callerId, LocalDateTime now) {
        if (agent.getCallAnsweredTime() == null) {
            // Primeira vez que o ramal entra em 1: marca o início
            String id = resolveCallerId(callerId, agent);
            if (!hasCallerId(id)) return;
            agent.setCallAnsweredTime(now);
            agent.setLastAnsweredCallerId(id);
            agent.setAttendanceRegistered(false);
            log.info("[ATENDIMENTO-INICIO] {} → {}", id, agent.getNomeAgente());

        } else if (!agent.isAttendanceRegistered()) {
            // Aguardando os 15s de confirmação
            if (Duration.between(agent.getCallAnsweredTime(), now).getSeconds() >= 15) {
                String id = agent.getLastAnsweredCallerId();
                globalMetricsService.registrarAtendimento(id, agent.getCallAnsweredTime());
                globalMetricsService.addCallDetails(id, now);
                agent.getLigacoes().add(new CallDetail(id, now));
                agent.setAttendanceRegistered(true);
                log.info("[ATENDIMENTO-CONFIRMADO] {} → {}", id, agent.getNomeAgente());
            }
        }
    }

    private void encerrarAtendimento(AgentStatus agent, LocalDateTime now) {
        if (agent.getCallAnsweredTime() == null) return;

        if (!agent.isAttendanceRegistered()) {
            // Chamada curta (< 15s): registra com callAnsweredTime para não confundir wasAttendedAfter
            String id = agent.getLastAnsweredCallerId();
            if (hasCallerId(id)) {
                globalMetricsService.registrarAtendimento(id, agent.getCallAnsweredTime());
                globalMetricsService.addCallDetails(id, agent.getCallAnsweredTime());
                agent.getLigacoes().add(new CallDetail(id, agent.getCallAnsweredTime()));
                log.info("[ATENDIMENTO-CURTO] {} → {} (< 15s)", id, agent.getNomeAgente());
                // Verifica se a chamada voltou para a fila e foi abandonada
                agendarVerificacaoPosCurto(id, now);
            }
        }
        // Se attendanceRegistered=true: encerramento normal ou repasse — sem ação

        agent.setCallAnsweredTime(null);
        agent.setLastAnsweredCallerId(null);
        agent.setAttendanceRegistered(false);
    }

    // ─── Verificação de desfecho (toque sem atender: removido ou abandonada) ─────
    //
    // Fluxo: agente perdeu chamada no toque (8→0).
    // Verifica se outro agente atendeu depois → removido.
    // Se não: recheck enquanto na fila. Esgotado → abandonada.

    private void agendarVerificacaoDesfecho(AgentStatus agent, String callerId, LocalDateTime eventTime) {
        String key = callerId + ":" + agent.getId();
        if (pendingCallChecks.getIfPresent(key) != null) return;
        pendingCallChecks.put(key, Boolean.TRUE);
        log.info("[DESFECHO-AGENDADO] agente={} callerId={}", agent.getNomeAgente(), callerId);
        scheduler.schedule(() -> verificarDesfecho(agent, callerId, eventTime, key, 1), 15, TimeUnit.SECONDS);
    }

    private void verificarDesfecho(AgentStatus agent, String callerId, LocalDateTime eventTime, String key, int tentativa) {
        boolean encerrou = false;
        try {
            if (globalMetricsService.wasAttendedAfter(callerId, eventTime)) {
                agent.setRemovido(agent.getRemovido() + 1);
                log.info("[REMOVIDO] agente={} callerId={}", agent.getNomeAgente(), callerId);
                encerrou = true;
                return;
            }

            // Enquanto na fila: sempre aguarda, sem limite de tentativas
            if (globalMetricsService.estaCallIdNaFila(callerId)) {
                log.info("[DESFECHO-AGUARDANDO-FILA] callerId={} tentativa={}", callerId, tentativa);
                scheduler.schedule(() -> verificarDesfecho(agent, callerId, eventTime, key, tentativa + 1), 10, TimeUnit.SECONDS);
                return;
            }

            // Fora da fila: aguarda confirmação do agente até MAX_TENTATIVAS_FILA
            if (algumAgenteComCallerId(callerId) && tentativa < MAX_TENTATIVAS_FILA) {
                log.info("[DESFECHO-AGUARDANDO-AGENTE] callerId={} tentativa={}/{}", callerId, tentativa, MAX_TENTATIVAS_FILA);
                scheduler.schedule(() -> verificarDesfecho(agent, callerId, eventTime, key, tentativa + 1), 20, TimeUnit.SECONDS);
                return;
            }

            registrarAbandonada(callerId, agent.getNomeAgente(), tentativa, "[ABANDONADA]");
            encerrou = true;

        } catch (Exception e) {
            log.error("[DESFECHO-ERRO] callerId={}: {}", callerId, e.getMessage(), e);
            encerrou = true;
        } finally {
            if (encerrou) pendingCallChecks.invalidate(key);
        }
    }

    // ─── Verificação pós-curto (atendeu mas pode ter voltado para a fila) ────────
    //
    // Fluxo: agente atendeu brevemente (< 15s).
    // Verifica se outro atendeu depois → não é abandono.
    // Se não: recheck enquanto na fila. Esgotado → abandonada.

    private void agendarVerificacaoPosCurto(String callerId, LocalDateTime eventTime) {
        String key = "poscurto:" + callerId;
        if (pendingCallChecks.getIfPresent(key) != null) return;
        pendingCallChecks.put(key, Boolean.TRUE);
        log.info("[POS-CURTO-AGENDADO] callerId={}", callerId);
        scheduler.schedule(() -> verificarPosCurto(callerId, eventTime, key, 1), 15, TimeUnit.SECONDS);
    }

    private void verificarPosCurto(String callerId, LocalDateTime eventTime, String key, int tentativa) {
        boolean encerrou = false;
        try {
            if (globalMetricsService.wasAttendedAfter(callerId, eventTime)) {
                log.info("[POS-CURTO-ATENDIDA] callerId={} atendida novamente, não é abandono.", callerId);
                encerrou = true;
                return;
            }

            // Enquanto na fila: sempre aguarda, sem limite de tentativas
            if (globalMetricsService.estaCallIdNaFila(callerId)) {
                log.info("[POS-CURTO-AGUARDANDO-FILA] callerId={} tentativa={}", callerId, tentativa);
                scheduler.schedule(() -> verificarPosCurto(callerId, eventTime, key, tentativa + 1), 10, TimeUnit.SECONDS);
                return;
            }

            // Fora da fila: aguarda confirmação do agente até MAX_TENTATIVAS_FILA
            if (algumAgenteComCallerId(callerId) && tentativa < MAX_TENTATIVAS_FILA) {
                log.info("[POS-CURTO-AGUARDANDO-AGENTE] callerId={} tentativa={}/{}", callerId, tentativa, MAX_TENTATIVAS_FILA);
                scheduler.schedule(() -> verificarPosCurto(callerId, eventTime, key, tentativa + 1), 20, TimeUnit.SECONDS);
                return;
            }

            registrarAbandonada(callerId, "pós-curto", tentativa, "[POS-CURTO-ABANDONADA]");
            encerrou = true;

        } catch (Exception e) {
            log.error("[POS-CURTO-ERRO] callerId={}: {}", callerId, e.getMessage(), e);
            encerrou = true;
        } finally {
            if (encerrou) pendingCallChecks.invalidate(key);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    // Só incrementa se ainda não foi contada (dois agentes podem chegar aqui para o mesmo callerId)
    private void registrarAbandonada(String callerId, String origem, int tentativas, String logTag) {
        if (processedAsAbandoned.getIfPresent(callerId) == null) {
            processedAsAbandoned.put(callerId, Boolean.TRUE);
            globalMetricsService.incrementChamadasAbandonadas();
            log.info("\n{} callerId={} origem={} tentativas={}\n", logTag, callerId, origem, tentativas);
        } else {
            log.debug("[ABANDONADA-SKIP] callerId={} já contabilizada.", callerId);
        }
    }

    // Retorna true se algum agente está atendendo OU com a chamada tocando agora
    private boolean algumAgenteComCallerId(String callerId) {
        return agents.values().stream().anyMatch(a ->
                callerId.equals(a.getLastAnsweredCallerId()) &&
                        (a.getCallAnsweredTime() != null || a.getRingingStartTime() != null));
    }

    private static boolean hasCallerId(String callerId) {
        return callerId != null && !callerId.isEmpty();
    }

    private static String resolveCallerId(String callerId, AgentStatus agent) {
        return hasCallerId(callerId) ? callerId : agent.getLastAnsweredCallerId();
    }

    private String defineAgentRole(String id) {
        if (id == null || id.length() <= 11) return "MEDICO";
        if (id.startsWith("1")) return "TARM";
        if (id.startsWith("2")) return "FROTA";
        return "OUTRO";
    }

    private void atualizarMetricasGlobais() {
        long total = agents.values().stream().mapToLong(AgentStatus::getChamadasRecebidasTotal).sum();
        globalMetricsService.addChamadasRecebidas(total);
    }

    // ─── Acesso externo ───────────────────────────────────────────────────────────

    public Map<String, AgentStatus> getAllAgentStatuses() {

        return agents.entrySet().stream()
                .filter(entry -> entry.getValue().getNomeAgente() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    // ─── Persistência ────────────────────────────────────────────────────────────

    @Transactional
    public void persistCurrentState() {
        LocalDate today = LocalDate.now();
        for (AgentStatus agent : agents.values()) {
            if (agent.getUltimoStatusAcd() == 0) continue;
            AgentDailyStats stats = agentStatsRepository
                    .findByAgentIdAndDate(agent.getId(), today)
                    .orElse(new AgentDailyStats());
            stats.setAgentId(agent.getId());
            stats.setAgentName(agent.getNomeAgente());
            stats.setDate(today);
            stats.setChamadasRecebidasTotal(agent.getChamadasRecebidasTotal());
            stats.setPausasIniciadasTotal(agent.getPausasIniciadasTotal());
            stats.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos());
            stats.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos());
            stats.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos());
            stats.setTempoTotalToqueSegundos(agent.getTempoTotalToqueSegundos());
            stats.setRemovidos(agent.getRemovido());
            stats.setUltimaAtualizacao(LocalDateTime.now());
            agentStatsRepository.save(stats);
        }
    }

    @Transactional
    public void loadFromDatabase() {
        agentStatsRepository.findByDate(LocalDate.now()).forEach(stats -> {
            AgentStatus agent = new AgentStatus();
            agent.setId(stats.getAgentId());
            agent.setNomeAgente(stats.getAgentName());
            agent.setChamadasRecebidasTotal(stats.getChamadasRecebidasTotal());
            agent.setPausasIniciadasTotal(stats.getPausasIniciadasTotal());
            agent.setTempoTotalPausaSegundos(stats.getTempoTotalPausaSegundos());
            agent.setTempoTotalLigacaoSegundos(stats.getTempoTotalLigacaoSegundos());
            agent.setTempoTotalLivreSegundos(stats.getTempoTotalLivreSegundos());
            agent.setTempoTotalToqueSegundos(stats.getTempoTotalToqueSegundos());
            agent.setRemovido(stats.getRemovidos());
            agents.put(agent.getId(), agent);
        });
    }

    public void resetAllAgentStatuses() {
        agents.clear();
        agentStatsRepository.deleteAll();
    }
}