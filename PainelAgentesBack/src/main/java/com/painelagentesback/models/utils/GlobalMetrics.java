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
    private long totalChamadasAtendidas;
    private long totalchamadasRecebidas;
    private long chamadasAbandonadas;
    private long chamadasEmFila;
    private long tempoTotalToqueSegundosGlobal;
    private List<CallDetail> allCallDetails = new CopyOnWriteArrayList<>();

    // Métodos sincronizados para atualizações thread-safe
    public synchronized void incrementTotalChamadas() {
        this.totalChamadasAtendidas++;
    }

    public synchronized void addCallDetails(String callerIdRAni, LocalDateTime timestamp) {
        allCallDetails.add(new CallDetail(callerIdRAni, timestamp));
    }

    public synchronized void addTotalToqueSegundos(long seconds) {
        this.tempoTotalToqueSegundosGlobal += seconds;
    }

    public List<CallDetail> getAllCallDetails() {
        return Collections.unmodifiableList(allCallDetails);
    }

    public synchronized void clearCallDetails() {
        allCallDetails.clear();
    }
}