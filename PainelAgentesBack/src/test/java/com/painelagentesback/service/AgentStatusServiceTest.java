package com.painelagentesback.service;

import com.painelagentesback.models.api.EventDetails;
import com.painelagentesback.models.api.HistoryItem;
import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import com.painelagentesback.service.clients.ConsultaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStatusServiceTest {

    @Mock
    private GlobalMetricsService globalMetricsService;

    @Mock
    private AgentDailyStatsRepository agentStatsRepository;

    @Mock
    private ConsultaClient consultaClient;

    @Mock
    private ScheduledExecutorService scheduler;

    @InjectMocks
    private AgentStatusService agentStatusService;

    private AgentsApi baseDto;

    @BeforeEach
    void setUp() {
        baseDto = new AgentsApi();
        baseDto.setId("123456789012"); // TARM
        baseDto.setNAgente("Agente Teste");
        baseDto.setStatus(1); // Ativo
        baseDto.setSTATUS_(0); // Livre
        baseDto.setNChAcd(10);
        baseDto.setCallerIdRAni("11987654321");
    }

    @Nested
    @DisplayName("Testes de updateAgentStatus")
    class UpdateAgentStatusTests {

        @Test
        @DisplayName("Deve ignorar DTO com status 0")
        void shouldIgnoreDtoWithStatusZero() {
            baseDto.setStatus(0);
            agentStatusService.updateAgentStatus(baseDto);
            verifyNoInteractions(globalMetricsService);
        }

        @Test
        @DisplayName("Deve criar novo agente se não existir")
        void shouldCreateNewAgent() {
            agentStatusService.updateAgentStatus(baseDto);
            Map<String, AgentStatus> agents = agentStatusService.getAllAgentStatuses();
            assertTrue(agents.containsKey("123456789012"));
            assertEquals("Agente Teste", agents.get("123456789012").getNomeAgente());
        }

        @Test
        @DisplayName("Deve definir Role corretamente (TARM)")
        void shouldDefineTarmRole() {
            baseDto.setId("123456789012");
            agentStatusService.updateAgentStatus(baseDto);
            AgentStatus agent = agentStatusService.getAllAgentStatuses().get("123456789012");
            assertEquals("TARM", agent.getAgentRole());
        }

        @Test
        @DisplayName("Deve definir Role corretamente (MEDICO)")
        void shouldDefineMedicoRole() {
            baseDto.setId("999");
            agentStatusService.updateAgentStatus(baseDto);
            AgentStatus agent = agentStatusService.getAllAgentStatuses().get("999");
            assertEquals("MEDICO", agent.getAgentRole());
        }

        @Test
        @DisplayName("Deve trackear chamada global se ramal for 8 (Toque)")
        void shouldTrackGlobalCallOnRinging() {
            baseDto.setSTATUS_(8);
            agentStatusService.updateAgentStatus(baseDto);
            verify(globalMetricsService).trackGlobalCall(eq("11987654321"), any());
        }

        @Test
        @DisplayName("Deve trackear chamada global se ramal for 1 (Atendimento)")
        void shouldTrackGlobalCallOnAnswered() {
            baseDto.setSTATUS_(1);
            agentStatusService.updateAgentStatus(baseDto);
            verify(globalMetricsService).trackGlobalCall(eq("11987654321"), any());
        }
    }

    @Nested
    @DisplayName("Testes de Acúmulo de Tempos")
    class TimeAccumulationTests {

        @Test
        @DisplayName("Deve acumular tempo de ligação")
        void shouldAccumulateCallTime() {
            // Primeiro update para setar o estado inicial
            baseDto.setSTATUS_(1); // Em ligação
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            LocalDateTime initialChange = LocalDateTime.now().minusSeconds(10);
            agent.setMudancaRamal(initialChange);

            // Segundo update mudando status para livre (0)
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            assertTrue(agent.getTempoTotalLigacaoSegundos() >= 10);
        }

        @Test
        @DisplayName("Deve acumular tempo de pausa")
        void shouldAccumulatePauseTime() {
            baseDto.setStatus(5); // Pausa (ACD status)
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            agent.setMudancaRamal(LocalDateTime.now().minusSeconds(20));

            baseDto.setStatus(1); // Volta para ativo
            agentStatusService.updateAgentStatus(baseDto);

            assertTrue(agent.getTempoTotalPausaSegundos() >= 20);
        }

        @Test
        @DisplayName("Deve incrementar contador de pausas")
        void shouldIncrementPauseCount() {
            // Estado inicial: Ativo (1)
            agentStatusService.updateAgentStatus(baseDto);

            // Muda para Pausa (5)
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(1, agent.getPausasIniciadasTotal());
        }
    }

    @Nested
    @DisplayName("Testes de Lógica de Toque e Atendimento")
    class RingingAndAnswerLogicTests {

        @Test
        @DisplayName("Deve iniciar toque (ramal 8)")
        void shouldStartRinging() {
            // Cria agente em estado livre primeiro
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            // Transiciona para tocando
            baseDto.setSTATUS_(8);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertNotNull(agent.getRingingStartTime());
            assertEquals(baseDto.getCallerIdRAni(), agent.getLastAnsweredCallerId());
        }

        @Test
        @DisplayName("Deve agendar verificação de desfecho se parar de tocar sem atender")
        void shouldScheduleOutcomeCheckWhenRingingStopsWithoutAnswering() {
            // Cria agente em estado livre primeiro
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            // Inicia toque
            baseDto.setSTATUS_(8);
            agentStatusService.updateAgentStatus(baseDto);

            // Para de tocar (volta para 0)
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            verify(scheduler).schedule(any(Runnable.class), eq(15L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Deve confirmar atendimento após tempo mínimo (3s)")
        void shouldConfirmAttendanceAfterMinDuration() {
            // Inicia atendimento (ramal 1)
            baseDto.setSTATUS_(1);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            agent.setCallAnsweredTime(LocalDateTime.now().minusSeconds(4));

            // Envia update novamente para confirmar
            agentStatusService.updateAgentStatus(baseDto);

            assertTrue(agent.isAttendanceRegistered());
            verify(globalMetricsService).registrarAtendimentoConfirmado(anyString(), any());
        }

        @Test
        @DisplayName("Deve tratar atendimento curto (< 3s)")
        void shouldHandleShortAttendance() {
            // Cria agente em estado livre primeiro
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            // Inicia atendimento (ramal 1)
            baseDto.setSTATUS_(1);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());

            // Encerra atendimento antes dos 3s (vai para ramal 0)
            baseDto.setSTATUS_(0);
            agentStatusService.updateAgentStatus(baseDto);

            // Simula que 2+ segundos se passaram fora do ramal 1
            // para disparar o encerramento via pendingEncerramento
            agent.setPendingEncerramento(LocalDateTime.now().minusSeconds(3));

            // Terceiro update dispara o encerramento efetivo
            agentStatusService.updateAgentStatus(baseDto);

            assertNull(agent.getCallAnsweredTime());
            // Deve agendar verificação pós-curto
            verify(scheduler).schedule(any(Runnable.class), eq(15L), eq(TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Testes de Persistência e Carga")
    class PersistenceTests {

        @Test
        @DisplayName("Deve persistir estado atual")
        void shouldPersistCurrentState() {
            // Adiciona um agente ativo
            agentStatusService.updateAgentStatus(baseDto);

            when(agentStatsRepository.findByAgentIdAndDate(anyString(), any())).thenReturn(Optional.empty());

            agentStatusService.persistCurrentState();

            verify(agentStatsRepository).save(any(AgentDailyStats.class));
        }

        @Test
        @DisplayName("Deve carregar dados do banco")
        void shouldLoadFromDatabase() {
            AgentDailyStats stats = new AgentDailyStats();
            stats.setAgentId("123");
            stats.setAgentName("Agente DB");
            stats.setDate(LocalDate.now());

            when(agentStatsRepository.findByDate(any())).thenReturn(List.of(stats));

            agentStatusService.loadFromDatabase();

            Map<String, AgentStatus> agents = agentStatusService.getAllAgentStatuses();
            assertTrue(agents.containsKey("123"));
            assertEquals("Agente DB", agents.get("123").getNomeAgente());
        }
    }

    @Nested
    @DisplayName("Testes de Pausas e Tradução")
    class PauseAndTranslationTests {

        @Test
        @DisplayName("Deve traduzir tipos de pausa corretamente")
        void shouldTranslatePauseTypes() {
            HistoryItem item = HistoryItem.builder()
                    .type("LUNCH")
                    .duration("00:30:00")
                    .date("10/10/2023")
                    .build();

            Map<String, EventDetails> agentMap = new HashMap<>();
            EventDetails details = new EventDetails();
            details.setHistory(List.of(item));
            agentMap.put("agent1", details);

            Map<String, Map<String, EventDetails>> response = new HashMap<>();
            response.put("data", agentMap);

            when(consultaClient.consult(any())).thenReturn(response);

            List<HistoryItem> results = agentStatusService.getPausas("123");

            assertEquals(1, results.size());
            assertEquals("Almoço", results.getFirst().getType());
        }
    }

    @Nested
    @DisplayName("Testes de Helpers e Utilitários")
    class HelperTests {

        @Test
        @DisplayName("Deve validar CallerId corretamente")
        void shouldValidateCallerId() {
            // Usando reflexão ou testando via comportamento público
            // Como hasCallerId é privado/estático, testamos via updateAgentStatus
            baseDto.setCallerIdRAni("123"); // Inválido (< 8 dígitos)
            baseDto.setSTATUS_(8);
            agentStatusService.updateAgentStatus(baseDto);
            verify(globalMetricsService, never()).trackGlobalCall(anyString(), any());

            baseDto.setCallerIdRAni("11987654321"); // Válido
            agentStatusService.updateAgentStatus(baseDto);
            verify(globalMetricsService).trackGlobalCall(eq("11987654321"), any());
        }
    }

    // ─── Testes das correções de bugs ────────────────────────────────────────────

    @Nested
    @DisplayName("Correção: Contagem falsa de pausas pós-reset (agente 24h)")
    class FalsePauseCountFixTests {

        @Test
        @DisplayName("Agente novo que já chega em pausa (ACD=5) NÃO deve contar pausa no primeiro ciclo")
        void shouldNotCountPauseWhenNewAgentAlreadyInPause() {
            // Agente aparece pela primeira vez já em pausa
            baseDto.setStatus(5);  // ACD = pausa
            baseDto.setSTATUS_(0); // ramal livre
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            // BUG antigo: pausasIniciadasTotal seria 1 porque prevAcd=0 != currAcd=5
            assertEquals(0, agent.getPausasIniciadasTotal(),
                    "Não deve contar pausa quando o agente já nasce em estado de pausa");
        }

        @Test
        @DisplayName("Agente existente transicionando de ativo para pausa DEVE contar normalmente")
        void shouldCountPauseWhenExistingAgentTransitionsToPause() {
            // Primeiro ciclo: agente ativo
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);

            // Segundo ciclo: transiciona para pausa
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(1, agent.getPausasIniciadasTotal(),
                    "Deve contar pausa quando há transição real de ativo→pausa");
        }

        @Test
        @DisplayName("Após reset, agente 24h que reaparece em pausa NÃO deve ter pausa contada")
        void shouldNotCountPauseAfterResetWhenAgentReturnsInPause() {
            // Agente ativo → pausa (1 pausa real)
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agentBefore = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(1, agentBefore.getPausasIniciadasTotal());

            // Reset (simula dailyReset_evening)
            agentStatusService.resetAllAgentStatuses();

            // Agente reaparece já em pausa (cenário do agente 24h)
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agentAfter = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(0, agentAfter.getPausasIniciadasTotal(),
                    "Pós-reset, agente 24h que reaparece em pausa não deve ter pausa contada");
        }

        @Test
        @DisplayName("Múltiplas transições de pausa devem contar corretamente")
        void shouldCountMultiplePauseTransitionsCorrectly() {
            // Ativo
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);

            // Pausa 1
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            // Volta ativo
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);

            // Pausa 2
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(2, agent.getPausasIniciadasTotal(),
                    "Deve contar exatamente 2 pausas em duas transições reais");
        }

        @Test
        @DisplayName("Estado inicial do ramal deve ser preservado pelo computeIfAbsent")
        void shouldPreserveInitialRamalStatus() {
            // Agente novo chega já tocando (ramal=8)
            baseDto.setSTATUS_(8);
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus agent = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            // Não deve ter iniciado ringing porque prevRamal == currRamal (ambos 8)
            // O ringingStartTime só é setado quando há transição para 8
            assertNull(agent.getRingingStartTime(),
                    "Não deve iniciar ringing quando o agente já aparece com ramal=8");
        }
    }

    @Nested
    @DisplayName("Correção: Race condition persistMetrics vs dailyReset")
    class PersistMetricsRaceConditionFixTests {

        @Test
        @DisplayName("persistCurrentState NÃO deve persistir quando flag resetting está ativo")
        void shouldNotPersistDuringReset() throws Exception {
            // Adiciona agente com dados
            agentStatusService.updateAgentStatus(baseDto);

            // Simula flag de reset ativo via reflection
            var field = AgentStatusService.class.getDeclaredField("resetting");
            field.setAccessible(true);
            field.set(agentStatusService, true);

            try {
                agentStatusService.persistCurrentState();

                // Não deve interagir com o banco
                verify(agentStatsRepository, never()).findByAgentIdAndDate(anyString(), any());
                verify(agentStatsRepository, never()).save(any(AgentDailyStats.class));
            } finally {
                field.set(agentStatusService, false);
            }
        }

        @Test
        @DisplayName("persistCurrentState DEVE funcionar normalmente quando NÃO está em reset")
        void shouldPersistNormallyWhenNotResetting() {
            agentStatusService.updateAgentStatus(baseDto);

            when(agentStatsRepository.findByAgentIdAndDate(anyString(), any()))
                    .thenReturn(Optional.empty());

            agentStatusService.persistCurrentState();

            verify(agentStatsRepository).save(any(AgentDailyStats.class));
        }

        @Test
        @DisplayName("Flag resetting deve voltar a false mesmo se deleteAll lançar exceção")
        void shouldResetFlagEvenOnException() throws Exception {
            doThrow(new RuntimeException("DB error")).when(agentStatsRepository).deleteAll();

            try {
                agentStatusService.resetAllAgentStatuses();
            } catch (RuntimeException ignored) {
                // Esperado
            }

            // Verifica que o flag voltou a false
            var field = AgentStatusService.class.getDeclaredField("resetting");
            field.setAccessible(true);
            assertFalse((boolean) field.get(agentStatusService),
                    "Flag resetting deve voltar a false no finally, mesmo com exceção");
        }

        @Test
        @DisplayName("Após reset completo, persist deve funcionar novamente")
        void shouldPersistAfterResetCompletes() {
            // Adiciona agente
            agentStatusService.updateAgentStatus(baseDto);

            // Executa reset
            agentStatusService.resetAllAgentStatuses();

            // Re-adiciona agente (simula collectAndProcessData repovoando)
            agentStatusService.updateAgentStatus(baseDto);

            when(agentStatsRepository.findByAgentIdAndDate(anyString(), any()))
                    .thenReturn(Optional.empty());

            // persistCurrentState deve funcionar normalmente
            agentStatusService.persistCurrentState();

            verify(agentStatsRepository).save(any(AgentDailyStats.class));
        }
    }

    @Nested
    @DisplayName("Testes de Reset")
    class ResetBehaviorTests {

        @Test
        @DisplayName("resetAllAgentStatuses deve limpar mapa de agentes e banco")
        void shouldClearAgentsAndDatabase() {
            agentStatusService.updateAgentStatus(baseDto);
            assertFalse(agentStatusService.getAllAgentStatuses().isEmpty(),
                    "Mapa deve ter agentes antes do reset");

            agentStatusService.resetAllAgentStatuses();

            assertTrue(agentStatusService.getAllAgentStatuses().isEmpty(),
                    "Mapa deve estar vazio após reset");
            verify(agentStatsRepository).deleteAll();
        }

        @Test
        @DisplayName("Agentes repovoados após reset devem começar zerados")
        void shouldStartFreshAfterReset() {
            // Agente com dados acumulados
            baseDto.setStatus(1);
            agentStatusService.updateAgentStatus(baseDto);
            baseDto.setStatus(5);
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus before = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(1, before.getPausasIniciadasTotal());

            // Reset
            agentStatusService.resetAllAgentStatuses();

            // Repovoar com o mesmo agente
            baseDto.setStatus(5); // ainda em pausa
            agentStatusService.updateAgentStatus(baseDto);

            AgentStatus after = agentStatusService.getAllAgentStatuses().get(baseDto.getId());
            assertEquals(0, after.getPausasIniciadasTotal(),
                    "Contadores devem estar zerados após reset");
            assertEquals(0, after.getTempoTotalPausaSegundos(),
                    "Tempos devem estar zerados após reset");
            assertEquals(0, after.getTempoTotalLigacaoSegundos(),
                    "Tempos devem estar zerados após reset");
        }
    }
}
