package com.painelagentesback.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.painelagentesback.models.api.*;
import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.enitity.CallDetail;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import com.painelagentesback.service.clients.ConsultaClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final ConsultaClient consultaClient;

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

    // Máximo de rechecks enquanto a chamada ainda está na fila ou com agente (10s cada → 100s total)
    private static final int MAX_TENTATIVAS_FILA = 15;

    // Máximo de rechecks durante a janela morta de roteamento (10s cada → 80s total)
    private static final int MAX_TENTATIVAS_TRANSITO = 8;

    /** Duração mínima (segundos) para uma ligação ser confirmada como atendida.
     *  Abaixo disso é tratada como "atendimento curto" e entra no fluxo pós-curto. */
    private static final long MIN_DURACAO_CONFIRMACAO_SEGUNDOS = 3;
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
        agent.setId(dto.getId());
        agent.setNomeAgente(dto.getNAgente());
        agent.setAgentRole(defineAgentRole(dto.getId()));
        agent.setChamadasRecebidasTotal(dto.getNChAcd());

        int prevRamal = agent.getUltimoStatusRamal();
        int currRamal = dto.getSTATUS_();
        int prevAcd = agent.getUltimoStatusAcd();
        int currAcd = dto.getStatus();
        String callerId = dto.getCallerIdRAni();
        LocalDateTime now = LocalDateTime.now();

        if (agent.getNomeAgente() == null || agent.getAgentRole() == null || agent.getAgentRole().isEmpty()) {
            return;
        }

        globalMetricsService.contarChamadas();

        if (hasCallerId(callerId) && (currRamal == 8 || currRamal == 1))
            globalMetricsService.trackGlobalCall(callerId, now);

        try {
            acumularTempos(agent, currAcd,currRamal, prevRamal);
            processarToque(agent, prevRamal, currRamal, callerId, now);
            processarAtendimento(agent, currRamal, callerId, now);
            contarPausas(agent, prevAcd, currAcd);
        } catch (Exception e) {
            log.warn("Falha em métricas secundárias para {}: {}", agent.getNomeAgente(), e.getMessage());
        }

        agent.setUltimoStatusAcd(currAcd);
        agent.setUltimoStatusRamal(currRamal);
//        atualizarMetricasGlobais();
    }

    // ─── Tempos contínuos ────────────────────────────────────────────────────────

    private void acumularTempos(AgentStatus agent, int acd, int ramal, int prevRamal) {
        LocalDateTime now = LocalDateTime.now();
        int prevAcd = agent.getUltimoStatusAcd();

        if (prevRamal != ramal || prevAcd != acd) {
            if (agent.getMudancaRamal() != null) {
                long segundosDecorridos = Duration.between(agent.getMudancaRamal(), now).getSeconds();
                acumularSegundos(agent, prevRamal, prevAcd, segundosDecorridos);
            }

            agent.setMudancaRamal(now);
            agent.setUltimoStatusRamal(ramal);
            agent.setUltimoStatusAcd(acd);
        }
    }

    private void acumularSegundos(AgentStatus agent, int ramal, int acd, long segundos) {
        if (ramal == 1) {
            // Está em ligação
            agent.setTempoTotalLigacaoSegundos(agent.getTempoTotalLigacaoSegundos() + segundos);
        }
        else if (acd == 5) {
            // Está em pausa
            agent.setTempoTotalPausaSegundos(agent.getTempoTotalPausaSegundos() + segundos);
        }
        else if (ramal == 0) {
            // Só conta como livre se não estiver em ligação E não estiver em pausa
            agent.setTempoTotalLivreSegundos(agent.getTempoTotalLivreSegundos() + segundos);
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
            if (agent.getPendingEncerramento() != null) {
                log.info("[ENCERRAMENTO-CANCELADO] ramal voltou para 1, callerId={}", agent.getLastAnsweredCallerId());
                agent.setPendingEncerramento(null);
            }
            iniciarOuConfirmarAtendimento(agent, callerId, now);
        } else {
            if (agent.getCallAnsweredTime() != null && agent.getPendingEncerramento() == null) {
                agent.setPendingEncerramento(now);
            } else if (agent.getPendingEncerramento() != null) {
                long segundosForaDoRamal = Duration.between(agent.getPendingEncerramento(), now).getSeconds();
                if (segundosForaDoRamal >= 2) {
                    LocalDateTime momentoEncerramento = agent.getPendingEncerramento();
                    agent.setPendingEncerramento(null);
                    encerrarAtendimento(agent, momentoEncerramento);
                }
            } else {
                encerrarAtendimento(agent, now);
            }
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
            // Aguardando os 3s de confirmação
            if (Duration.between(agent.getCallAnsweredTime(), now).getSeconds() >= MIN_DURACAO_CONFIRMACAO_SEGUNDOS) {
                String id = agent.getLastAnsweredCallerId();
                globalMetricsService.registrarAtendimento(id, agent.getCallAnsweredTime());
                globalMetricsService.addCallDetails(id, now);
                globalMetricsService.registrarAtendimentoConfirmado(id, now);
                agent.getLigacoes().add(new CallDetail(id, now));
                agent.setAttendanceRegistered(true);
                log.info("[ATENDIMENTO-CONFIRMADO] {} → {}", id, agent.getNomeAgente());
                globalMetricsService.incrementTotalChamadas(agents.values());

            }
        }
    }

    private void encerrarAtendimento(AgentStatus agent, LocalDateTime now) {
        if (agent.getCallAnsweredTime() == null) return;

        if (!agent.isAttendanceRegistered()) {
            String id = agent.getLastAnsweredCallerId();
            if (hasCallerId(id)) {
                long duracaoSegundos = Duration.between(agent.getCallAnsweredTime(), now).getSeconds();
                globalMetricsService.registrarAtendimento(id, agent.getCallAnsweredTime());
                globalMetricsService.addCallDetails(id, agent.getCallAnsweredTime());
                agent.getLigacoes().add(new CallDetail(id, agent.getCallAnsweredTime()));
                log.info("[ATENDIMENTO-CURTO] {} → {} ({}s)", id, agent.getNomeAgente(), duracaoSegundos);

                // Só agenda pós-curto se nenhum outro agente já está com a chamada ativa.
                // wasAttendedAfter cobre repasses TARM→TARM e TARM→MÉDICO (se médico gerar confirmação).
                if (!algumOutroAgenteComCallerId(id, agent)) {
                    agendarVerificacaoPosCurto(id, now);
                } else {
                    log.info("[POS-CURTO-SKIP] callerId={} outro agente já está atendendo.", id);
                }
            }
        }

        agent.setCallAnsweredTime(null);
        agent.setLastAnsweredCallerId(null);
        agent.setAttendanceRegistered(false);
    }

    // ─── Verificação de desfecho (toque sem atender: removido ou abandonada) ─────
    //
    // Fluxo: agente perdeu chamada no toque (8→0).
    // Verifica se outro agente atendeu e confirmou depois → removido.
    // Se não: recheck enquanto na fila ou com agente (MAX_TENTATIVAS_FILA).
    //         sem fila e sem agente → janela de trânsito (MAX_TENTATIVAS_TRANSITO).
    //         esgotado → abandonada.

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
                log.warn("\n[REMOVIDO] agente={} callerId={}", agent.getNomeAgente(), callerId);
                encerrou = true;
                return;
            }

            boolean naFila    = globalMetricsService.estaCallIdNaFila(callerId);
            boolean temAgente = algumAgenteComCallerId(callerId);
            int maxTentativas = (naFila || temAgente) ? MAX_TENTATIVAS_FILA : MAX_TENTATIVAS_TRANSITO;

            if (tentativa < maxTentativas) {
                String motivo = temAgente ? "AGUARDANDO-AGENTE" : naFila ? "AGUARDANDO-FILA" : "AGUARDANDO-TRANSITO";
                log.info("[DESFECHO-{}] callerId={} tentativa={}/{}", motivo, callerId, tentativa, maxTentativas);
                scheduler.schedule(() -> verificarDesfecho(agent, callerId, eventTime, key, tentativa + 1), 10, TimeUnit.SECONDS);
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
    // Se wasAttendedAfter=true (qualquer agente confirmou atendimento depois) → não é abandono.
    //   Isso cobre repasses TARM→TARM e TARM→MÉDICO (quando médico gera evento de confirmação).
    // Se não: recheck enquanto na fila ou com agente (MAX_TENTATIVAS_FILA).
    //         sem fila e sem agente → janela de trânsito (MAX_TENTATIVAS_TRANSITO).
    //         esgotado → abandono.
    //   Nota: falso positivo possível em transferências para médico sem evento de confirmação.
    //   Nesses casos, corrigir manualmente decrementando o contador de abandonadas.

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

            boolean naFila    = globalMetricsService.estaCallIdNaFila(callerId);
            boolean temAgente = algumAgenteComCallerId(callerId);
            int maxTentativas = (naFila || temAgente) ? MAX_TENTATIVAS_FILA : MAX_TENTATIVAS_TRANSITO;

            if (tentativa < maxTentativas) {
                String motivo = temAgente ? "AGUARDANDO-AGENTE" : naFila ? "AGUARDANDO-FILA" : "AGUARDANDO-TRANSITO";
                log.info("[POS-CURTO-{}] callerId={} tentativa={}/{}", motivo, callerId, tentativa, maxTentativas);
                scheduler.schedule(() -> verificarPosCurto(callerId, eventTime, key, tentativa + 1), 10, TimeUnit.SECONDS);
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
            log.warn("\n{} callerId={} origem={} tentativas={}\n", logTag, callerId, origem, tentativas);
        } else {
            log.warn("[ABANDONADA-SKIP] callerId={} já contabilizada.", callerId);
        }
    }

    // Retorna true se algum agente está atendendo OU com a chamada tocando agora
    private boolean algumAgenteComCallerId(String callerId) {
        return agents.values().stream().anyMatch(a ->
                callerId.equals(a.getLastAnsweredCallerId()) &&
                        (a.getCallAnsweredTime() != null || a.getRingingStartTime() != null));
    }

    // Igual ao anterior mas exclui o agente que acabou de encerrar o atendimento curto
    private boolean algumOutroAgenteComCallerId(String callerId, AgentStatus excludeAgent) {
        return agents.values().stream().anyMatch(a ->
                a != excludeAgent &&
                        callerId.equals(a.getLastAnsweredCallerId()) &&
                        (a.getCallAnsweredTime() != null || a.getRingingStartTime() != null));
    }

    private static boolean hasCallerId(String callerId) {
        if (callerId == null || callerId.trim().isEmpty()) {
            return false;
        }
        String numbersOnly = callerId.replaceAll("[^0-9]", "");
        return numbersOnly.length() >= 8 && numbersOnly.length() <= 13;
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

//    private void atualizarMetricasGlobais() {
//        long total = agents.values().stream().mapToLong(AgentStatus::getChamadasRecebidasTotal).sum();
//        globalMetricsService.addChamadasRecebidas(total);
//    }

    // ─── Acesso externo ───────────────────────────────────────────────────────────

    public Map<String, AgentStatus> getAllAgentStatuses() {
        return agents.entrySet().stream()
                .filter(entry -> entry.getValue().getNomeAgente() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    public List<HistoryItem> getPausas(String agentIds) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var date = LocalDate.now().format(dateFormatter);

        ApiOptions options = ApiOptions.builder()
                .history(true)
                .total(false)
                .duration_average(false)
                .build();

        ApiRequest request = ApiRequest.builder()
                .events(List.of("pause"))
                .agents_id(List.of(agentIds))
                .date_rage(DateRange.builder().start(date).end(date).build())
                .options(options)
                .build();
        try {
            Map<String, Map<String, EventDetails>> response = consultaClient.consult(request);
            var history = response.values().stream()
                    .findFirst()
                    .flatMap(agentMap -> agentMap.values().stream().findFirst())
                    .map(EventDetails::getHistory)
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(item -> !"CONFERENCE".equals(item.getType()))
                    .collect(Collectors.toList());
            return translateHistory(history);

        } catch (Exception e) {
            return List.of();
        }
    }

    private List<HistoryItem> translateHistory(List<HistoryItem> historyItems){
        List<HistoryItem> translated = new ArrayList<>();
        for (var history: historyItems){
           var newHistory =  HistoryItem
                   .builder()
                   .date(history.getDate())
                   .duration(history.getDuration())
                   .type(tralatedTypes(history.getType()))
                   .build();
           translated.add(newHistory);
        }
        return translated;
    }
    private String tralatedTypes(String type){
        return switch (type){
            case "BREAKFAST" -> "Café da Manhã";
            case "LUNCH" -> "Almoço";
            case "DINNER" -> "Jantar";
            case "AFTERNOON_COFFEE" -> "Café da Tarde";
            case "NIGHT_REST" -> "Descanso Noturno";
            case "BATHROOM_BREAK" -> "Pausa para Banheiro";
            default -> type;
        };
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
            stats.setUltimaMudancaStatus(agent.getMudancaRamal());
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
            agent.setMudancaRamal(stats.getUltimaMudancaStatus());
            agents.put(agent.getId(), agent);
        });
    }

    public void resetAllAgentStatuses() {
        agents.clear();
        agentStatsRepository.deleteAll();
    }
}