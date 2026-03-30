package com.painelagentesback.models.utils;

import com.painelagentesback.models.enitity.CallDetail;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AgentStatus {
    private String id;
    private String nomeAgente;
    private String agentRole;
    private int chamadasRecebidasTotal;
    private int chamadasAtendidasTotal;
    private int pausasIniciadasTotal;
    private long tempoTotalPausaSegundos;
    private long tempoTotalLigacaoSegundos;
    private long tempoTotalLivreSegundos;
    private long tempoTotalToqueSegundos;       // Tempo acumulado em que o ramal tocou (fila)
    private int removido;
    private int ultimoStatusAcd;                 // Último status ACD (1=logado, 5=pausa, etc.)
    private int ultimoStatusRamal;                // Último status do ramal (0=livre, 1=ocupado, 8=tocando)
    private LocalDateTime ringingStartTime;      // Início do último estado "tocando"
    private LocalDateTime callAnsweredTime;      // Tempo do chamado
    private String lastAnsweredCallerId;
    private boolean attendanceRegistered;
    private List<CallDetail> ligacoes = new CopyOnWriteArrayList<>();
    private LocalDateTime pauseStartTime;
    private LocalDateTime freeStartTime;
    private LocalDateTime callStartTime;
    private LocalDateTime pendingEncerramento;
    private LocalDateTime mudancaRamal;

}