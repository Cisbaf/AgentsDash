package com.painelagentesback.models.enitity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_daily_stats")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GlobalDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "total_chamadas")
    private long totalChamadas;

    @Column(name = "chamadas_atendidas")
    private long chamadasAtendidas;

    @Column(name = "chamadas_abandonadas")
    private long chamadasAbandonadas;

    @Column(name = "chamadas_em_fila")
    private long chamadasEmFila;

    @Column(name = "tempo_total_toque_global_segundos")
    private long tempoTotalToqueSegundosGlobal;

    @Column(name = "ultima_atualizacao")
    private LocalDateTime ultimaAtualizacao;
}
