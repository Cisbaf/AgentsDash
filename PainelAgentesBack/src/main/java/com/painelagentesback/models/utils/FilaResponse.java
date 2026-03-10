package com.painelagentesback.models.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@JacksonXmlRootElement(localName = "chamadas_fila")
@JsonIgnoreProperties(ignoreUnknown = true) // <-- IGNORA TAGS EXTRAS NO ROOT
public class FilaResponse {

    @JacksonXmlProperty(localName = "chamadas")
    private ChamadasWrapper chamadas;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true) // <-- IGNORA <retorno_descricao>
    public static class ChamadasWrapper {

        @JacksonXmlProperty(localName = "retorno")
        private String retorno;

        @JacksonXmlProperty(localName = "retorno_codigo")
        private String retornoCodigo;

        // (Opcional) Se quiser mapear a descrição, basta descomentar abaixo
        // @JacksonXmlProperty(localName = "retorno_descricao")
        // private String retornoDescricao;

        @JacksonXmlProperty(localName = "chamada_fila")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<ChamadaFilaItem> itens;
    }

    @Data
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true) // <-- IGNORA <custom_vars>
    public static class ChamadaFilaItem {

        @JacksonXmlProperty(localName = "fila")
        private String fila;

        @JacksonXmlProperty(localName = "numero")
        private String numero;

        @JacksonXmlProperty(localName = "duracao")
        private String duracao;

        @JacksonXmlProperty(localName = "uid")
        private String uid;

        @JacksonXmlProperty(localName = "protocol")
        private String protocol;
    }
}