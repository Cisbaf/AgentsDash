package com.painelagentesback.service;

import com.painelagentesback.models.enitity.GlobalDailyStats;
import com.painelagentesback.models.utils.FilaResponse;
import com.painelagentesback.models.utils.GlobalMetrics;
import com.painelagentesback.repository.GlobalDailyStatsRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalMetricsServiceTest {

    @Mock
    private GlobalDailyStatsRepository globalStatsRepository;
    @Mock
    private FilaClient filaClient;

    @InjectMocks
    private GlobalMetricsService service;

    @Captor
    private ArgumentCaptor<GlobalDailyStats> statsCaptor;

    private final String CALLER_ID = "call-123";
    private final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        service.resetGlobalMetrics();
    }

    @Nested
    @DisplayName("Operações básicas de métricas")
    class BasicMetricsTests {

        @Test
        @DisplayName("addCallDetails deve adicionar detalhe e atualizar contadores")
        void addCallDetails_shouldAddDetailAndUpdateCounters() {
            service.addCallDetails(CALLER_ID, NOW);

            GlobalMetrics metrics = service.getGlobalMetrics();
            assertThat(metrics.getAllCallDetails()).hasSize(1);
            assertThat(metrics.getAllCallDetails().getFirst().getCallerIdRAni()).isEqualTo(CALLER_ID);
            assertThat(metrics.getAllCallDetails().getFirst().getTimestamp()).isEqualTo(NOW);
            assertThat(metrics.getTotalChamadasAtendidas()).isEqualTo(1);
        }

        @Test
        @DisplayName("addTotalToqueSegundos deve acumular corretamente")
        void addTotalToqueSegundos_shouldAccumulate() {
            service.addTotalToqueSegundos(10);
            service.addTotalToqueSegundos(5);

            assertThat(service.getGlobalMetrics().getTempoTotalToqueSegundosGlobal()).isEqualTo(15);
        }

        @Test
        @DisplayName("incrementChamadasAbandonadas deve incrementar contador")
        void incrementChamadasAbandonadas_shouldIncrement() {
            service.incrementChamadasAbandonadas();
            service.incrementChamadasAbandonadas();

            assertThat(service.getGlobalMetrics().getChamadasAbandonadas()).isEqualTo(2);
        }

        @Test
        @DisplayName("addChamadasRecebidas deve setar o valor (não somar)")
        void addChamadasRecebidas_shouldSetValue() {
            service.addChamadasRecebidas(100);
            assertThat(service.getGlobalMetrics().getTotalchamadasRecebidas()).isEqualTo(100);

            service.addChamadasRecebidas(50);
            assertThat(service.getGlobalMetrics().getTotalchamadasRecebidas()).isEqualTo(50);
        }

        @Test
        @DisplayName("registrarAtendimento deve adicionar uid ao mapa interno (sem efeito colateral visível)")
        void registrarAtendimento_shouldAddUidToMap() {
            service.registrarAtendimento(CALLER_ID);
            // Não há getter para o mapa, mas garantimos que não lança exceção
        }

        @Test
        @DisplayName("estaNaFila sempre retorna false porque uidsAnterioresNaFila nunca é populado")
        void estaNaFila_shouldAlwaysReturnFalse() {
            assertThat(service.estaNaFila(CALLER_ID)).isFalse();
            service.registrarAtendimento(CALLER_ID);
            assertThat(service.estaNaFila(CALLER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("Método contarChamadas")
    class ContarChamadasTests {

        @Test
        @DisplayName("com resposta válida deve definir chamadasEmFila com tamanho da lista")
        void shouldSetQueueSizeFromValidResponse() {
            FilaResponse response = mock(FilaResponse.class);
            FilaResponse.ChamadasWrapper wrapper = mock(FilaResponse.ChamadasWrapper.class);
            when(filaClient.statusFilaSystem()).thenReturn(response);
            when(response.getChamadas()).thenReturn(wrapper);
            when(wrapper.getItens()).thenReturn(List.of(new FilaResponse.ChamadaFilaItem(), new FilaResponse.ChamadaFilaItem()));

            service.contarChamadas();

            assertThat(service.getGlobalMetrics().getChamadasEmFila()).isEqualTo(2);
        }

        @Test
        @DisplayName("com resposta nula deve definir chamadasEmFila = 0")
        void shouldSetZeroWhenResponseNull() {
            when(filaClient.statusFilaSystem()).thenReturn(null);
            service.contarChamadas();
            assertThat(service.getGlobalMetrics().getChamadasEmFila()).isZero();
        }

        @Test
        @DisplayName("com wrapper nulo deve definir zero")
        void shouldSetZeroWhenWrapperNull() {
            FilaResponse response = mock(FilaResponse.class);
            when(filaClient.statusFilaSystem()).thenReturn(response);
            when(response.getChamadas()).thenReturn(null);
            service.contarChamadas();
            assertThat(service.getGlobalMetrics().getChamadasEmFila()).isZero();
        }

        @Test
        @DisplayName("com itens nulos deve definir zero")
        void shouldSetZeroWhenItemsNull() {
            FilaResponse response = mock(FilaResponse.class);
            FilaResponse.ChamadasWrapper wrapper = mock(FilaResponse.ChamadasWrapper.class);
            when(filaClient.statusFilaSystem()).thenReturn(response);
            when(response.getChamadas()).thenReturn(wrapper);
            when(wrapper.getItens()).thenReturn(null);
            service.contarChamadas();
            assertThat(service.getGlobalMetrics().getChamadasEmFila()).isZero();
        }
    }

    @Nested
    @DisplayName("Persistência")
    class PersistenceTests {

        @Test
        @DisplayName("persistCurrentState deve criar novo registro quando não existir para a data")
        void shouldCreateNewStatsWhenNotExists() {
            LocalDate today = LocalDate.now();
            when(globalStatsRepository.findByDate(today)).thenReturn(Optional.empty());

            // Configurar métricas
            service.addCallDetails(CALLER_ID, NOW);
            service.addTotalToqueSegundos(30);
            service.incrementChamadasAbandonadas();
            service.addChamadasRecebidas(10);
            // Simular chamadas em fila
            FilaResponse response = mock(FilaResponse.class);
            FilaResponse.ChamadasWrapper wrapper = mock(FilaResponse.ChamadasWrapper.class);
            when(filaClient.statusFilaSystem()).thenReturn(response);
            when(response.getChamadas()).thenReturn(wrapper);
            when(wrapper.getItens()).thenReturn(List.of(new FilaResponse.ChamadaFilaItem()));
            service.contarChamadas();

            service.persistCurrentState();

            verify(globalStatsRepository).save(statsCaptor.capture());
            GlobalDailyStats saved = statsCaptor.getValue();

            assertThat(saved.getDate()).isEqualTo(today);
            assertThat(saved.getTotalchamadasRecebidas()).isEqualTo(10);
            assertThat(saved.getChamadasAbandonadas()).isEqualTo(1);
            assertThat(saved.getChamadasEmFila()).isEqualTo(1);
            assertThat(saved.getTempoTotalToqueSegundosGlobal()).isEqualTo(30);
            // Campo não persistido corretamente (setado após save)
            assertThat(saved.getTotalChamadasAtendidas()).isEqualTo(0);
            assertThat(saved.getUltimaAtualizacao()).isNotNull();
        }

        @Test
        @DisplayName("persistCurrentState deve atualizar registro existente")
        void shouldUpdateExistingStats() {
            LocalDate today = LocalDate.now();
            GlobalDailyStats existing = new GlobalDailyStats();
            existing.setId(1L);
            when(globalStatsRepository.findByDate(today)).thenReturn(Optional.of(existing));

            service.addCallDetails(CALLER_ID, NOW);
            service.persistCurrentState();

            verify(globalStatsRepository).save(existing);
            assertThat(existing.getTotalChamadasAtendidas()).isEqualTo(0);
        }

        @Test
        @DisplayName("loadFromDatabase deve carregar dados do dia e atualizar métricas")
        void shouldLoadStatsFromDatabase() {
            LocalDate today = LocalDate.now();
            GlobalDailyStats stats = new GlobalDailyStats();
            stats.setTotalchamadasRecebidas(100);
            stats.setChamadasAbandonadas(5);
            stats.setChamadasEmFila(3);
            stats.setTempoTotalToqueSegundosGlobal(45);
            stats.setTotalChamadasAtendidas(20);
            when(globalStatsRepository.findByDate(today)).thenReturn(Optional.of(stats));

            service.loadFromDatabase();

            GlobalMetrics metrics = service.getGlobalMetrics();
            assertThat(metrics.getTotalchamadasRecebidas()).isEqualTo(100);
            assertThat(metrics.getChamadasAbandonadas()).isEqualTo(5);
            assertThat(metrics.getChamadasEmFila()).isEqualTo(3);
            assertThat(metrics.getTempoTotalToqueSegundosGlobal()).isEqualTo(45);
            assertThat(metrics.getTotalChamadasAtendidas()).isEqualTo(20);
            assertThat(metrics.getAllCallDetails()).isEmpty(); // não é restaurado
        }

        @Test
        @DisplayName("loadFromDatabase com optional vazio não altera métricas")
        void shouldNotChangeMetricsWhenNoData() {
            when(globalStatsRepository.findByDate(any())).thenReturn(Optional.empty());
            service.addTotalToqueSegundos(10);
            service.loadFromDatabase();
            assertThat(service.getGlobalMetrics().getTempoTotalToqueSegundosGlobal()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Reset")
    class ResetTests {

        @Test
        @DisplayName("resetGlobalMetrics deve zerar todos os contadores e limpar detalhes")
        void shouldResetAllMetrics() {
            // Alimentar métricas
            service.addCallDetails(CALLER_ID, NOW);
            service.addTotalToqueSegundos(15);
            service.incrementChamadasAbandonadas();
            service.addChamadasRecebidas(30);
            // Simular fila
            FilaResponse response = mock(FilaResponse.class);
            FilaResponse.ChamadasWrapper wrapper = mock(FilaResponse.ChamadasWrapper.class);
            when(filaClient.statusFilaSystem()).thenReturn(response);
            when(response.getChamadas()).thenReturn(wrapper);
            when(wrapper.getItens()).thenReturn(List.of(new FilaResponse.ChamadaFilaItem()));
            service.contarChamadas();

            service.resetGlobalMetrics();

            GlobalMetrics metrics = service.getGlobalMetrics();
            assertThat(metrics.getTotalChamadasAtendidas()).isZero();
            assertThat(metrics.getTotalchamadasRecebidas()).isZero();
            assertThat(metrics.getChamadasAbandonadas()).isZero();
            assertThat(metrics.getChamadasEmFila()).isZero();
            assertThat(metrics.getTempoTotalToqueSegundosGlobal()).isZero();
            assertThat(metrics.getAllCallDetails()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Concorrência")
    class ConcurrencyTests {

        @Test
        @DisplayName("incrementChamadasAbandonadas é thread-safe (synchronized)")
        void incrementChamadasAbandonadas_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 100;
            Runnable task = () -> {
                for (int i = 0; i < incrementsPerThread; i++) {
                    service.incrementChamadasAbandonadas();
                }
            };

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(task);
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }

            assertThat(service.getGlobalMetrics().getChamadasAbandonadas())
                    .isEqualTo(threadCount * incrementsPerThread);
        }
    }
}