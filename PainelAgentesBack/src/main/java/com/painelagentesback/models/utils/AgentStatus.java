package com.painelagentesback.models.utils;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentStatus {
    private String id;
    private String nomeAgente;
    private int chamadasAtendidasTotal;
    private int pausasIniciadasTotal;
    private long tempoTotalPausaSegundos;
    private long tempoTotalLigacaoSegundos;
    private long tempoTotalLivreSegundos;
    private long tempoTotalToqueSegundos;       // Tempo acumulado em que o ramal tocou (fila)
    private boolean removido;
    private int ultimoStatusAcd;                 // Último status ACD (1=logado, 5=pausa, etc.)
    private int ultimoStatusRamal;                // Último status do ramal (0=livre, 1=ocupado, 8=tocando)
    private LocalDateTime ringingStartTime;      // Início do último estado "tocando"

}