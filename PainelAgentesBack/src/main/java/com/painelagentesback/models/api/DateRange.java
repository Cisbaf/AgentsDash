package com.painelagentesback.models.api;

import lombok.Builder;

@Builder
public record DateRange(String start, String end) {
}
