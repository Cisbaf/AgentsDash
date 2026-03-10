package com.painelagentesback.models.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
public class CallState {
    private final String callerId;
    @Setter
    private String primeiroAgenteId;
    @Setter
    private LocalDateTime callInitiationTime; //quando a chamada começou a tocar pela primeira vez
    private boolean finalizada;

    public CallState(String callerId) {
        this.callerId = callerId;
        this.callInitiationTime = LocalDateTime.now(); // Define o tempo de início da chamada
        this.finalizada = false;
    }

    public void finalizar() {
        this.finalizada = true;
    }

}
