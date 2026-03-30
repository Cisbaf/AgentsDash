package com.painelagentesback.models.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetails {
    private List<HistoryItem> history;
    private Integer total; // Mapeia o campo "total" que pode ser um número.

    @JsonProperty("duration_average") // Garante o mapeamento correto do JSON para o campo
    private String durationAverage;
}