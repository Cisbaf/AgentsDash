package com.painelagentesback.models.utils;

import com.painelagentesback.models.enitity.CallDetail;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GlobalMetrics {
    private long totalChamadas;
    private long chamadasAtendidas;
    private long chamadasAbandonadas;       // Necessita lógica adicional
    private long chamadasEmFila;             // Necessita lógica adicional
    private long tempoTotalToqueSegundosGlobal;
    private List<CallDetail> allCallDetails = new CopyOnWriteArrayList<>();

    // Métodos sincronizados para atualizações thread-safe
    public synchronized void incrementTotalChamadas() {
        this.totalChamadas++;
    }

    public synchronized void incrementChamadasAtendidas() {
        this.chamadasAtendidas++;
    }

    public synchronized void addCallDetails(String callerIdRAni, LocalDateTime timestamp) {
        allCallDetails.add(new CallDetail(callerIdRAni, timestamp));
    }

    public synchronized void addTotalToqueSegundos(long seconds) {
        this.tempoTotalToqueSegundosGlobal += seconds;
    }

    public CallDetail getFirstCallDetails() {
        return allCallDetails.isEmpty() ? null : allCallDetails.getFirst();
    }

    public CallDetail getLastCallDetails() {
        return allCallDetails.isEmpty() ? null : allCallDetails.getLast();
    }

    public List<CallDetail> getAllCallDetails() {
        return Collections.unmodifiableList(allCallDetails);
    }
}