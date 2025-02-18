package de.rwth.idsg.steve.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.joda.time.DateTime;

import java.util.List;

@Getter
@Builder
@ToString
public class MeterValue {
    @Schema(description = "Timestamp when the value was measured")
    private final DateTime timestamp;

    @Schema(description = "List of measured values")
    private final List<SampledValue> sampledValues;

    @Getter
    @Builder
    @ToString
    public static class SampledValue {
        @Schema(description = "Measured value")
        private final String value;

        @Schema(description = "Context of the measurement")
        private final String context;

        @Schema(description = "Format of the value")
        private final String format;

        @Schema(description = "Type of measurement")
        private final String measurand;

        @Schema(description = "Phase if applicable")
        private final String phase;

        @Schema(description = "Location of measurement")
        private final String location;

        @Schema(description = "Unit of measurement")
        private final String unit;
    }
}
