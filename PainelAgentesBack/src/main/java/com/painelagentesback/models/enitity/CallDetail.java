package com.painelagentesback.models.enitity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "call_detail")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caller_id")
    private String callerIdRAni;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "date")
    private LocalDate date;

    public CallDetail(String callerIdRAni, LocalDateTime timestamp) {
        this.callerIdRAni = callerIdRAni;
        this.timestamp = timestamp;
    }
}
