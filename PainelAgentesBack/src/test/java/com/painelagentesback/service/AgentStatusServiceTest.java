package com.painelagentesback.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.painelagentesback.models.enitity.AgentDailyStats;
import com.painelagentesback.models.enitity.AgentsApi;
import com.painelagentesback.models.utils.AgentStatus;
import com.painelagentesback.models.utils.FilaResponse;
import com.painelagentesback.repository.AgentDailyStatsRepository;
import com.painelagentesback.service.clients.FilaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStatusServiceTest {

    @Mock
    private GlobalMetricsService globalMetricsService;
    @Mock
    private AgentDailyStatsRepository agentStatsRepository;
    @Mock
    private FilaClient filaClient; // não usado diretamente, mas necessário para o construtor

    @InjectMocks
    private AgentStatusService service;

    @Captor
    private ArgumentCaptor<Long> longCaptor;
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<LocalDateTime> dateTimeCaptor;
    @Captor
    private ArgumentCaptor<AgentDailyStats> statsCaptor;

    private final String AGENT_ID = "12345";
    private final String AGENT_NAME = "João Silva";
    private final String CALLER_ID = "call-001";


    private AgentsApi createDto(int statusAcd, int statusRamal, String callerId) {
        com.painelagentesback.models.enitity.AgentsApi dto = new AgentsApi();
        dto.setId(AGENT_ID);
        dto.setNAgente(AGENT_NAME);
        dto.setStatus(statusAcd);
        dto.setSTATUS_(statusRamal);
        dto.setCallerIdRAni(callerId);
        dto.setNChAcd(5); // valor fixo para teste
        return dto;
    }

    @BeforeEach
    void setUp() {
        service.resetAllAgentStatuses();
    }

    @Nested
    @DisplayName("Testes de atualização sem toque (métricas contínuas)")
    class ContinuousMetricsTests {

        @Test
        @DisplayName("Status ACD = 0 deve ser ignorado (retorno imediato)")
        void shouldIgnoreWhenStatusZero() {
            AgentsApi dto = createDto(0, 1, CALLER_ID);
            service.updateAgentStatus(dto);

            assertThat(service.getAllAgentStatuses()).isEmpty();
            verifyNoInteractions(globalMetricsService);
        }

        @Test
        @DisplayName("Status ACD = 1 e Ramal = 1 deve incrementar tempo de ligação")
        void shouldIncrementCallTimeWhenAcd1AndRamal1() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(1);
            assertThat(agent.getTempoTotalLivreSegundos()).isZero();
            assertThat(agent.getTempoTotalPausaSegundos()).isZero();
        }

        @Test
        @DisplayName("Status ACD = 1 e Ramal = 0 deve incrementar tempo livre")
        void shouldIncrementFreeTimeWhenAcd1AndRamal0() {
            AgentsApi dto = createDto(1, 0, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLivreSegundos()).isEqualTo(1);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isZero();
        }

        @Test
        @DisplayName("Status ACD = 5 deve incrementar tempo de pausa e contador de pausas")
        void shouldIncrementPauseTimeAndCountWhenAcd5() {
            AgentsApi dto = createDto(5, 0, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(1);
            assertThat(agent.getPausasIniciadasTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("Múltiplas atualizações acumulam tempos corretamente")
        void shouldAccumulateTimesAcrossMultipleUpdates() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ligação
            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ligação
            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // livre
            service.updateAgentStatus(createDto(5, 0, CALLER_ID)); // pausa
            service.updateAgentStatus(createDto(5, 0, CALLER_ID)); // pausa

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(2);
            assertThat(agent.getTempoTotalLivreSegundos()).isEqualTo(1);
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(2);
            assertThat(agent.getPausasIniciadasTotal()).isEqualTo(1); // só incrementa na primeira entrada em pausa
        }

        @Test
        @DisplayName("Deve atualizar últimos status e chamadas recebidas")
        void shouldUpdateLastStatusAndReceivedCalls() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getUltimoStatusAcd()).isEqualTo(1);
            assertThat(agent.getUltimoStatusRamal()).isEqualTo(1);
            assertThat(agent.getChamadasRecebidasTotal()).isEqualTo(5);
        }

        @Test
        @DisplayName("Deve chamar globalMetricsService.contarChamadas a cada atualização")
        void shouldCallContarChamadasOnEveryUpdate() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));
            verify(globalMetricsService, times(2)).contarChamadas();
        }
    }

    @Nested
    @DisplayName("Testes de definição do papel do agente")
    class AgentRoleTests {

        @Test
        @DisplayName("ID com mais de 11 caracteres e começando com 1 -> TARM")
        void shouldSetTarmRole() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            dto.setId("123456789012"); // 12 caracteres
            service.updateAgentStatus(dto);
            assertThat(service.getAllAgentStatuses().get("123456789012").getAgentRole()).isEqualTo("TARM");
        }

        @Test
        @DisplayName("ID com mais de 11 caracteres e começando com 2 -> FROTA")
        void shouldSetFrotaRole() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            dto.setId("223456789012");
            service.updateAgentStatus(dto);
            assertThat(service.getAllAgentStatuses().get("223456789012").getAgentRole()).isEqualTo("FROTA");
        }

        @Test
        @DisplayName("ID com 11 caracteres ou menos -> MEDICO")
        void shouldSetMedicoRole() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            dto.setId("12345"); // 5 caracteres
            service.updateAgentStatus(dto);
            assertThat(service.getAllAgentStatuses().get("12345").getAgentRole()).isEqualTo("MEDICO");

            dto.setId("12345678901"); // 11 caracteres
            service.updateAgentStatus(dto);
            assertThat(service.getAllAgentStatuses().get("12345678901").getAgentRole()).isEqualTo("MEDICO");
        }
    }

    @Nested
    @DisplayName("Testes de lógica de toque (ringing)")
    class RingingLogicTests {

        @Test
        @DisplayName("Início do toque deve marcar ringingStartTime")
        void shouldSetRingingStartTimeWhenRamalBecomes8() {
            AgentsApi dto = createDto(1, 8, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRingingStartTime()).isNotNull();
        }

        @Test
        @DisplayName("Fim do toque sem início registrado não deve computar duração")
        void shouldNotComputeDurationIfRingingStartTimeNull() {
            // Primeiro, cria o agente sem toque
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            // Depois, fim de toque sem ter iniciado
            AgentsApi dto = createDto(1, 0, CALLER_ID);
            dto.setCallerIdRAni(CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalToqueSegundos()).isZero();
            verify(globalMetricsService, never()).addTotalToqueSegundos(anyLong());
        }

        @Test
        @DisplayName("Fim do toque com atendimento (ramal 1) deve computar duração e registrar atendimento")
        void shouldHandleAnswerAfterRinging() throws InterruptedException {
            // Início do toque
            service.updateAgentStatus(createDto(1, 8, CALLER_ID));

            // Aguarda o tempo necessário para contabilizar pelo menos 1 segundo
            Thread.sleep(1100);

            // Atendimento
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalToqueSegundos()).isPositive();
            assertThat(agent.getRingingStartTime()).isNull();
            assertThat(agent.getRemovido()).isZero();

            verify(globalMetricsService).addTotalToqueSegundos(longCaptor.capture());
            assertThat(longCaptor.getValue()).isPositive();

            verify(globalMetricsService).registrarAtendimento(CALLER_ID);
            verify(globalMetricsService).addCallDetails(eq(CALLER_ID), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Fim do toque sem atendimento e chamada ainda na fila -> removido +1")
        void shouldIncrementRemovidoWhenNotAnsweredAndStillInQueue() {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(true);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // não atendeu

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isEqualTo(1);
            verify(globalMetricsService).estaNaFila(CALLER_ID);
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Fim do toque sem atendimento e chamada NÃO está na fila -> removido não incrementa (abandono tratado em outro lugar)")
        void shouldNotIncrementRemovidoWhenNotAnsweredAndNotInQueue() {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService).estaNaFila(CALLER_ID);
        }

        @Test
        @DisplayName("Fim do toque sem callerId (null ou vazio) não processa lógica de removido/atendimento")
        void shouldSkipRemovidoLogicWhenCallerIdIsEmpty() {
            service.updateAgentStatus(createDto(1, 8, null));
            service.updateAgentStatus(createDto(1, 0, ""));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).estaNaFila(anyString());
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Múltiplos toques acumulam tempo total corretamente")
        void shouldAccumulateTotalRingingTime() throws InterruptedException {
            when(globalMetricsService.estaNaFila(anyString())).thenReturn(false);

            // Primeiro toque
            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            Thread.sleep(1100); // Aguarda pouco mais de 1 segundo
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));

            // Segundo toque
            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            Thread.sleep(1100); // Aguarda pouco mais de 1 segundo
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalToqueSegundos()).isPositive();
            verify(globalMetricsService, times(2)).addTotalToqueSegundos(anyLong());
        }
    }

    @Nested
    @DisplayName("Testes de métricas globais (totalChamadasRecebidas)")
    class GlobalMetricsIntegrationTests {

        @Test
        @DisplayName("Deve somar chamadas recebidas de todos os agentes e atualizar globalMetricsService")
        void shouldSumAllAgentsReceivedCalls() {
            // Agente 1
            AgentsApi dto1 = createDto(1, 1, CALLER_ID);
            dto1.setId("1");
            dto1.setNChAcd(10);
            service.updateAgentStatus(dto1);

            // Agente 2
            AgentsApi dto2 = createDto(1, 1, CALLER_ID);
            dto2.setId("2");
            dto2.setNChAcd(20);
            service.updateAgentStatus(dto2);

            verify(globalMetricsService).addChamadasRecebidas(10);
            verify(globalMetricsService).addChamadasRecebidas(30); // 10+20
        }
    }

    @Nested
    @DisplayName("Testes de persistência (banco de dados)")
    class PersistenceTests {

        @Test
        @DisplayName("persistCurrentState deve salvar apenas agentes com ultimoStatusAcd != 0")
        void shouldPersistOnlyActiveAgents() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            service.persistCurrentState();

            verify(agentStatsRepository, times(1)).save(any(AgentDailyStats.class));
        }

        @Test
        @DisplayName("persistCurrentState deve criar novo ou atualizar estatísticas existentes")
        void shouldCreateOrUpdateStats() {
            LocalDate today = LocalDate.now();
            AgentDailyStats existingStats = new AgentDailyStats();
            existingStats.setId(1L);
            when(agentStatsRepository.findByAgentIdAndDate(AGENT_ID, today))
                    .thenReturn(Optional.of(existingStats));

            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            service.persistCurrentState();

            verify(agentStatsRepository).findByAgentIdAndDate(AGENT_ID, today);
            verify(agentStatsRepository).save(statsCaptor.capture());
            AgentDailyStats saved = statsCaptor.getValue();
            assertThat(saved.getAgentId()).isEqualTo(AGENT_ID);
            assertThat(saved.getAgentName()).isEqualTo(AGENT_NAME);
            assertThat(saved.getChamadasRecebidasTotal()).isEqualTo(5);
            assertThat(saved.getTempoTotalLigacaoSegundos()).isEqualTo(1);
        }

        @Test
        @DisplayName("loadFromDatabase deve carregar estatísticas do dia e popular o mapa agents")
        void shouldLoadFromDatabase() {
            LocalDate today = LocalDate.now();
            AgentDailyStats stats = new AgentDailyStats();
            stats.setAgentId(AGENT_ID);
            stats.setAgentName(AGENT_NAME);
            stats.setChamadasRecebidasTotal(10);
            stats.setPausasIniciadasTotal(2);
            stats.setTempoTotalPausaSegundos(30);
            stats.setTempoTotalLigacaoSegundos(120);
            stats.setTempoTotalLivreSegundos(60);
            stats.setTempoTotalToqueSegundos(15);
            stats.setUltimaAtualizacao(LocalDateTime.now());

            when(agentStatsRepository.findByDate(today)).thenReturn(List.of(stats));

            service.loadFromDatabase();

            Map<String, AgentStatus> agents = service.getAllAgentStatuses();
            assertThat(agents).containsKey(AGENT_ID);
            AgentStatus agent = agents.get(AGENT_ID);
            assertThat(agent.getId()).isEqualTo(AGENT_ID);
            assertThat(agent.getNomeAgente()).isEqualTo(AGENT_NAME);
            assertThat(agent.getChamadasRecebidasTotal()).isEqualTo(10);
            assertThat(agent.getPausasIniciadasTotal()).isEqualTo(2);
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(30);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(120);
            assertThat(agent.getTempoTotalLivreSegundos()).isEqualTo(60);
            assertThat(agent.getTempoTotalToqueSegundos()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Testes de reset")
    class ResetTests {

        @Test
        @DisplayName("resetAllAgentStatuses deve limpar o mapa de agentes")
        void shouldClearAgentsMap() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            assertThat(service.getAllAgentStatuses()).isNotEmpty();

            service.resetAllAgentStatuses();
            assertThat(service.getAllAgentStatuses()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Testes de concorrência (simulação)")
    class ConcurrencyTests {

        @Test
        @DisplayName("Atualizações concorrentes no mesmo agente devem ser seguras")
        void shouldHandleConcurrentUpdatesSafely() throws InterruptedException {
            // Este teste executa 10 threads atualizando o mesmo agente simultaneamente
            // e verifica se o estado final é consistente.
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AgentsApi dto = createDto(1, 1, CALLER_ID);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> service.updateAgentStatus(dto));
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            // Como cada atualização incrementa o tempo em 1 segundo, e todas são status 1/1,
            // o tempo total de ligação deve ser threadCount.
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("Testes de integração com o mapa calls (se aplicável)")
    class CallStateMapTests {
        // O mapa 'calls' não é utilizado no AgentStatusService, mas se futuramente for,
        // estes testes garantem que o método getCallState existe e retorna um CallState.
        // Como o método é privado, não podemos testá-lo diretamente sem reflexão.
        // Por enquanto, apenas verificamos que o mapa não interfere na lógica atual.

        @Test
        @DisplayName("O mapa calls não é usado na lógica de updateAgentStatus (cobertura indireta)")
        void callsMapIsNotUsed() {
            // Não há interações com calls no update, então não há o que testar.
            // Este teste é apenas documental.
        }
    }

    @Nested
    @DisplayName("Testes de serialização do FilaResponse (exemplo)")
    class FilaResponseSerializationTests {

        @Test
        @DisplayName("Deve desserializar XML corretamente")
        void shouldDeserializeFilaResponse() throws Exception {
            String xml = """
                    <chamadas_fila>
                      <chamadas>
                        <retorno>0</retorno>
                        <retorno_codigo>200</retorno_codigo>
                        <chamada_fila>
                          <fila>F1</fila>
                          <numero>1234</numero>
                          <duracao>10</duracao>
                          <uid>uid-1</uid>
                          <protocol>TCP</protocol>
                        </chamada_fila>
                        <chamada_fila>
                          <fila>F2</fila>
                          <numero>5678</numero>
                          <duracao>20</duracao>
                          <uid>uid-2</uid>
                          <protocol>UDP</protocol>
                        </chamada_fila>
                      </chamadas>
                    </chamadas_fila>""";

            XmlMapper xmlMapper = new XmlMapper();
            FilaResponse response = xmlMapper.readValue(xml, FilaResponse.class);

            assertThat(response.getChamadas().getRetorno()).isEqualTo("0");
            assertThat(response.getChamadas().getRetornoCodigo()).isEqualTo("200");
            assertThat(response.getChamadas().getItens()).hasSize(2);
            assertThat(response.getChamadas().getItens().get(0).getUid()).isEqualTo("uid-1");
            assertThat(response.getChamadas().getItens().get(1).getUid()).isEqualTo("uid-2");
        }

        @Test
        @DisplayName("Deve ignorar tags extras (retorno_descricao, custom_vars)")
        void shouldIgnoreExtraTags() throws Exception {
            String xml = """
                    <chamadas_fila>
                      <chamadas>
                        <retorno>0</retorno>
                        <retorno_codigo>200</retorno_codigo>
                        <retorno_descricao>Sucesso</retorno_descricao>
                        <chamada_fila>
                          <fila>F1</fila>
                          <numero>1234</numero>
                          <duracao>10</duracao>
                          <uid>uid-1</uid>
                          <protocol>TCP</protocol>
                          <custom_vars><var1>val1</var1></custom_vars>
                        </chamada_fila>
                      </chamadas>
                    </chamadas_fila>""";

            XmlMapper xmlMapper = new XmlMapper();
            FilaResponse response = xmlMapper.readValue(xml, FilaResponse.class);

            assertThat(response.getChamadas().getItens()).hasSize(1);
            assertThat(response.getChamadas().getItens().getFirst().getUid()).isEqualTo("uid-1");
            // Não deve lançar exceção devido às tags extras
        }
    }
    @Nested
    @DisplayName("Testes de transições de status sem toque")
    class StatusTransitionTests {

        @Test
        @DisplayName("Transição direta de livre (0) para ocupado (1) deve incrementar tempo de ligação")
        void shouldIncrementCallTimeWhenDirectFromFreeToOccupied() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            service.updateAgentStatus(dto);

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(1);
            assertThat(agent.getTempoTotalLivreSegundos()).isZero();
        }

        @Test
        @DisplayName("Transição direta de pausa (5) para ocupado (1) deve incrementar tempo de ligação")
        void shouldIncrementCallTimeWhenFromPauseToOccupied() {
            // Primeiro coloca em pausa
            service.updateAgentStatus(createDto(5, 0, CALLER_ID));
            // Depois vai para ocupado
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(1);
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(1);
        }

        @Test
        @DisplayName("Transição direta de ocupado (1) para pausa (5) deve incrementar tempo de pausa e contador de pausas")
        void shouldIncrementPauseWhenFromOccupiedToPause() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));
            service.updateAgentStatus(createDto(5, 0, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(1);
            assertThat(agent.getPausasIniciadasTotal()).isEqualTo(1);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(1);
        }

        @Test
        @DisplayName("Múltiplas transições sem toque acumulam tempos corretamente")
        void shouldAccumulateTimesAcrossMultipleTransitions() throws InterruptedException {
            // Sequência: livre -> ocupado -> livre -> ocupado -> pausa
            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // livre
            Thread.sleep(1100);

            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ocupado
            Thread.sleep(1100);

            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // livre
            Thread.sleep(1100);

            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ocupado
            Thread.sleep(1100);

            service.updateAgentStatus(createDto(5, 0, CALLER_ID)); // pausa

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLivreSegundos()).isEqualTo(2); // duas vezes livre
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(2); // duas vezes ocupado
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(1);
            assertThat(agent.getPausasIniciadasTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("Atualizações com mesmo status consecutivas devem acumular tempo")
        void shouldAccumulateTimeWhenSameStatusRepeated() {
            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ocupado
            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ocupado de novo
            service.updateAgentStatus(createDto(1, 1, CALLER_ID)); // ocupado de novo

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalLigacaoSegundos()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Testes de lógica de toque com abandono")
    class RingingWithAbandonTests {

        @Test
        @DisplayName("Fim do toque sem atendimento e chamada NÃO está na fila deve incrementar chamadas abandonadas no GlobalMetrics")
        void shouldIncrementAbandonedWhenNotAnsweredAndNotInQueue() {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Fim do toque com atendimento não deve incrementar abandonadas")
        void shouldNotIncrementAbandonedWhenAnswered() {
            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            service.updateAgentStatus(createDto(1, 1, CALLER_ID));

            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService).registrarAtendimento(CALLER_ID);
        }

        @Test
        @DisplayName("Fim do toque com remoção (ainda na fila) não deve incrementar abandonadas")
        void shouldNotIncrementAbandonedWhenRemoved() {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(true);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID));
            service.updateAgentStatus(createDto(1, 0, CALLER_ID));

            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService).estaNaFila(CALLER_ID);
        }

        @Test
        @DisplayName("Fim do toque sem callerId não deve incrementar abandonadas nem removido")
        void shouldNotIncrementAnythingWhenCallerIdIsEmpty() {
            service.updateAgentStatus(createDto(1, 8, null));
            service.updateAgentStatus(createDto(1, 0, ""));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
            verify(globalMetricsService, never()).estaNaFila(anyString());
        }

        @Test
        @DisplayName("Fim do toque indo para pausa (status 5) deve processar corretamente")
        void shouldHandleRingingEndingInPause() throws InterruptedException {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID));

            Thread.sleep(1100);

            service.updateAgentStatus(createDto(5, 0, CALLER_ID)); // vai para pausa

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getTempoTotalToqueSegundos()).isPositive();
            assertThat(agent.getTempoTotalPausaSegundos()).isEqualTo(1);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService).incrementChamadasAbandonadas();
        }
    }

    @Nested
    @DisplayName("Testes de papel do agente (casos extremos)")
    class AgentRoleEdgeCasesTests {

        @Test
        @DisplayName("ID vazio deve retornar MEDICO")
        void shouldReturnMedicoWhenIdEmpty() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            dto.setId("");
            service.updateAgentStatus(dto);
            AgentStatus agent = service.getAllAgentStatuses().get("");
            assertThat(agent).isNotNull();
            assertThat(agent.getAgentRole()).isEqualTo("MEDICO");
        }

        @Test
        @DisplayName("ID com 12 caracteres começando com 3 deve retornar OUTRO")
        void shouldReturnOutroForUnrecognizedPrefix() {
            AgentsApi dto = createDto(1, 1, CALLER_ID);
            dto.setId("312345678901"); // 12 chars, começa com 3
            service.updateAgentStatus(dto);
            AgentStatus agent = service.getAllAgentStatuses().get("312345678901");
            assertThat(agent).isNotNull();
            assertThat(agent.getAgentRole()).isEqualTo("OUTRO");
        }
    }

    @Nested
    @DisplayName("Testes adicionais de lógica de toque (removidos e abandonados)")
    class AdditionalRingingLogicTests {

        @Test
        @DisplayName("Chamada abandonada: duração de toque exatamente 10 segundos e não está na fila")
        void shouldNotIncrementAbandonedWhenRingingDurationIsExactly10Seconds() throws InterruptedException {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID)); // Início do toque

            // Simula 10 segundos de toque
            Thread.sleep(10000); // 10 segundos

            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // Fim do toque, não atendido

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).incrementChamadasAbandonadas(); // Não deve ser abandonada
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Chamada não abandonada: duração de toque maior que 10 segundos e não está na fila")
        void shouldNotIncrementAbandonedWhenRingingDurationIsMoreThan10Seconds() throws InterruptedException {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID)); // Início do toque

            // Simula mais de 10 segundos de toque
            Thread.sleep(11000); // 11 segundos

            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // Fim do toque, não atendido

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).incrementChamadasAbandonadas(); // Não deve ser abandonada
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Chamada removida: transição de toque para livre (ramal 0) e ainda na fila")
        void shouldIncrementRemovidoWhenRingingEndsInFreeAndStillInQueue() throws InterruptedException {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(true);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID)); // Início do toque
            Thread.sleep(100); // Pequeno delay para garantir duration > 0
            service.updateAgentStatus(createDto(1, 0, CALLER_ID)); // Fim do toque, livre, ainda na fila

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isEqualTo(1);
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Chamada removida: transição de toque para pausa (ramal 0, acd 5) e ainda na fila")
        void shouldIncrementRemovidoWhenRingingEndsInPauseAndStillInQueue() throws InterruptedException {
            when(globalMetricsService.estaNaFila(CALLER_ID)).thenReturn(true);

            service.updateAgentStatus(createDto(1, 8, CALLER_ID)); // Início do toque
            Thread.sleep(100); // Pequeno delay para garantir duration > 0
            service.updateAgentStatus(createDto(5, 0, CALLER_ID)); // Fim do toque, pausa, ainda na fila

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isEqualTo(1);
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Múltiplas chamadas removidas para o mesmo agente")
        void shouldHandleMultipleRemovedCallsForSameAgent() throws InterruptedException {
            when(globalMetricsService.estaNaFila(anyString())).thenReturn(true);

            // Primeira chamada
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-1"));
            Thread.sleep(100);
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-1"));

            // Segunda chamada
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-2"));
            Thread.sleep(100);
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-2"));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isEqualTo(2);
            verify(globalMetricsService, times(2)).estaNaFila(anyString());
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
        }

        @Test
        @DisplayName("Múltiplas chamadas abandonadas para o mesmo agente")
        void shouldHandleMultipleAbandonedCallsForSameAgent() throws InterruptedException {
            when(globalMetricsService.estaNaFila(anyString())).thenReturn(false);

            // Primeira chamada abandonada (duração < 10s)
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-1"));
            Thread.sleep(5000); // 5 segundos
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-1"));

            // Segunda chamada abandonada (duração < 10s)
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-2"));
            Thread.sleep(7000); // 7 segundos
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-2"));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, times(2)).incrementChamadasAbandonadas();
            verify(globalMetricsService, times(2)).estaNaFila(anyString());
        }

        @Test
        @DisplayName("Cenário misto: uma removida e uma abandonada para o mesmo agente")
        void shouldHandleMixedRemovedAndAbandonedCallsForSameAgent() throws InterruptedException {
            // Primeira chamada: removida (ainda na fila)
            when(globalMetricsService.estaNaFila(CALLER_ID + "-1")).thenReturn(true);
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-1"));
            Thread.sleep(100);
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-1"));

            // Segunda chamada: abandonada (não está na fila, duração < 10s)
            when(globalMetricsService.estaNaFila(CALLER_ID + "-2")).thenReturn(false);
            service.updateAgentStatus(createDto(1, 8, CALLER_ID + "-2"));
            Thread.sleep(5000); // 5 segundos
            service.updateAgentStatus(createDto(1, 0, CALLER_ID + "-2"));

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isEqualTo(1);
            verify(globalMetricsService, times(1)).incrementChamadasAbandonadas();
            verify(globalMetricsService, times(2)).estaNaFila(anyString());
        }

        @Test
        @DisplayName("Chamada abandonada: duração de toque menor que 10 segundos, mas callerId nulo")
        void shouldNotIncrementAbandonedWhenCallerIdIsNull() throws InterruptedException {
            when(globalMetricsService.estaNaFila(anyString())).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, null)); // Início do toque com callerId nulo
            Thread.sleep(5000); // 5 segundos
            service.updateAgentStatus(createDto(1, 0, null)); // Fim do toque, não atendido, callerId nulo

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }

        @Test
        @DisplayName("Chamada abandonada: duração de toque menor que 10 segundos, mas callerId vazio")
        void shouldNotIncrementAbandonedWhenCallerIdIsEmptyString() throws InterruptedException {
            when(globalMetricsService.estaNaFila(anyString())).thenReturn(false);

            service.updateAgentStatus(createDto(1, 8, "")); // Início do toque com callerId vazio
            Thread.sleep(5000); // 5 segundos
            service.updateAgentStatus(createDto(1, 0, "")); // Fim do toque, não atendido, callerId vazio

            AgentStatus agent = service.getAllAgentStatuses().get(AGENT_ID);
            assertThat(agent.getRemovido()).isZero();
            verify(globalMetricsService, never()).incrementChamadasAbandonadas();
            verify(globalMetricsService, never()).registrarAtendimento(anyString());
        }
    }
}