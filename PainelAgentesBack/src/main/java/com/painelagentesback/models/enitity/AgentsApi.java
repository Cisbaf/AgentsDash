package com.painelagentesback.models.enitity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentsApi {
    private String id;
    @JsonProperty("n_agente")
    private String nAgente;
    private String nickname;
    private int status;                     // status ACD
    private String channel;
    private String tecnologia;
    @JsonProperty("tp_de_pausa")
    private String tpDePausa;
    @JsonProperty("date_status")
    private String dateStatus;               // duração do status corrente
    private boolean dialer;
    @JsonProperty("TAB_wait")
    private boolean tabWait;
    @JsonProperty("DIALER_CAMPANHA")
    private String dialerCampanha;
    @JsonProperty("DIALER_CLIENTE")
    private String dialerCliente;
    @JsonProperty("DIALER_CAMPOS")
    private String dialerCampos;
    @JsonProperty("DIALER_VALORES")
    private String dialerValores;
    @JsonProperty("DIALER_STATUS")
    private int dialerStatus;
    @JsonProperty("STATUS_")
    private int STATUS_;                     // status do ramal
    @JsonProperty("n_ch_acd")
    private int nChAcd;                       // chamadas atendidas no dia (cumulativo)
    @JsonProperty("t_call")
    private int tCall;
    @JsonProperty("last_date")
    private String lastDate;                  // duração do status do ramal
    private int tTAcd;
    private String uid;
    @JsonProperty("r_agente_time")
    private String rAgenteTime;
    @JsonProperty("VIRTUAL_GRP_CALLERID")
    private String virtualGrpCallerid;
    @JsonProperty("o_serv")
    private String oServ;
    @JsonProperty("CALLERID_R_ANI")
    private String callerIdRAni;               // identificador da chamada atual
    private String callerid;
    private String msg;
    private String tab;
    private String remoteview;
    @JsonProperty("custom_vars")
    private String customVars;
    private String protocol;
    @JsonProperty("date_now")
    private String dateNow;
}
