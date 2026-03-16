package com.painelagentesback.models.enitity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_daily_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "date"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "agent_role")
    private String agentRole;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "chamadas_recebidas")
    private int chamadasRecebidasTotal;

    @Column(name = "pausas_iniciadas")
    private int pausasIniciadasTotal;

    @Column(name = "tempo_total_pausa_segundos")
    private long tempoTotalPausaSegundos;

    @Column(name = "tempo_total_ligacao_segundos")
    private long tempoTotalLigacaoSegundos;

    @Column(name = "tempo_total_livre_segundos")
    private long tempoTotalLivreSegundos;

    @Column(name = "tempo_total_toque_segundos")
    private long tempoTotalToqueSegundos;

    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;

    private int removidos;
}