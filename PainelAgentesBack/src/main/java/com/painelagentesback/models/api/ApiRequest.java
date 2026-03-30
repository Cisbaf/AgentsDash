package com.painelagentesback.models.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Builder
@ToString
public class ApiRequest {
    private List<String> events;
    private List<String> agents_id;
    private DateRange date_rage;
    private ApiOptions options;


}
