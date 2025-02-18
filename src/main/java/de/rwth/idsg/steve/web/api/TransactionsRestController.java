/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2025 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.web.api;

import de.rwth.idsg.steve.repository.TransactionRepository;
import de.rwth.idsg.steve.repository.dto.Transaction;
import de.rwth.idsg.steve.repository.dto.TransactionDetails;
import de.rwth.idsg.steve.web.api.ApiControllerAdvice.ApiErrorResponse;
import de.rwth.idsg.steve.web.api.exception.BadRequestException;
import de.rwth.idsg.steve.web.dto.TransactionQueryForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;
import org.jooq.impl.SQLDataType;
import org.jooq.DataType;
import java.sql.Timestamp;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 13.09.2022
 */
@Tag(name = "transaction-controller",
    description = """
        Operations related to querying transactions.
        A transaction represents a charging session at a charge box (i.e. charging station. The notions 'charge box' and 'charging station' are being used interchangeably).
        """
)
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransactionsRestController {

    private final TransactionRepository transactionRepository;
    
    // Define the DateTime data type with proper conversion
    private static final DataType<DateTime> DATE_TIME_TYPE = 
        SQLDataType.TIMESTAMP.asConvertedDataType(
            new DateTimeConverter()
        );

    // Custom converter for Joda DateTime
    private static class DateTimeConverter implements org.jooq.Converter<Timestamp, DateTime> {
        @Override
        public DateTime from(Timestamp timestamp) {
            return timestamp != null ? new DateTime(timestamp.getTime()) : null;
        }

        @Override
        public Timestamp to(DateTime dateTime) {
            return dateTime != null ? new Timestamp(dateTime.getMillis()) : null;
        }

        @Override
        public Class<Timestamp> fromType() {
            return Timestamp.class;
        }

        @Override
        public Class<DateTime> toType() {
            return DateTime.class;
        }
    }

    @Operation(description = """
        Returns a list of transactions based on the query parameters.
        The query parameters can be used to filter the transactions.
        """)
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))})}
    )
    @GetMapping(value = "")
    @ResponseBody
    public List<Transaction> get(@Valid TransactionQueryForm.ForApi params) {
        log.debug("Read request for query: {}", params);

        if (params.isReturnCSV()) {
            throw new BadRequestException("returnCSV=true is not supported for API calls");
        }

        var response = transactionRepository.getTransactions(params);
        log.debug("Read response for query: {}", response);
        return response;
    }

    @Operation(description = "Returns detailed information about a specific transaction including meter values")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "Transaction not found", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))})
})
    @GetMapping("/{transactionId}")
    @ResponseBody
    public TransactionDetails getDetails(@PathVariable("transactionId") int transactionId) {
        log.debug("Get details for transaction {}", transactionId);
        return transactionRepository.getDetails(transactionId);
    }

    @Operation(description = "Returns simplified transaction information with all meter values")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "Transaction not found", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))})
    })
    @GetMapping("/{transactionId}/latest")
    @ResponseBody
    public Map<String, Object> getLatestDetails(@PathVariable("transactionId") int transactionId) {
        log.debug("Get latest details for transaction {}", transactionId);
        TransactionDetails details = transactionRepository.getDetails(transactionId);

        Map<String, Object> response = new HashMap<>();
        response.put("id", transactionId);
        response.put("chargeBoxId", details.getTransaction().getChargeBoxId());
        response.put("connectorId", details.getTransaction().getConnectorId());
        response.put("startTimestamp", details.getTransaction().getStartTimestamp());
        response.put("startValue", details.getTransaction().getStartValue());
        response.put("timestamp", DateTime.now());
        response.put("connectorStatus", details.getConnectorStatus());
        response.put("chargingTime", details.getChargingTime());

        // Group meter values by measurand type and get the latest for each
        Map<String, TransactionDetails.MeterValues> latestByType = new HashMap<>();
        details.getValues().forEach(mv -> {
            String measurand = mv.getMeasurand().toLowerCase();
            String key;
            if (measurand.contains("energy") && measurand.contains("active") && measurand.contains("import")) {
                key = "energy.active.import.register";
            } else if (measurand.contains("temperature")) {
                key = "temperature";
            } else if (measurand.contains("current") && measurand.contains("import")) {
                key = "current.import";
            } else if (measurand.contains("power") && measurand.contains("active") && measurand.contains("import")) {
                key = "power.active.import";
            } else if (measurand.contains("voltage")) {
                key = "voltage";
            } else {
                return;
            }

            // Update if this is the first value or if this value is more recent
            if (!latestByType.containsKey(key) || 
                mv.getValueTimestamp().isAfter(latestByType.get(key).getValueTimestamp())) {
                latestByType.put(key, mv);
            }
        });

        // Find the latest timestamp across all selected meter values
        Optional<DateTime> latestTimestamp = latestByType.values().stream()
            .map(TransactionDetails.MeterValues::getValueTimestamp)
            .max(DateTime::compareTo);

        Map<String, Object> meterValuesWrapper = new HashMap<>();
        if (latestTimestamp.isPresent()) {
            DateTime timestamp = latestTimestamp.get();
            Map<String, Map<String, String>> meterValues = new HashMap<>();
            
            latestByType.forEach((key, mv) -> {
                Map<String, String> valueMap = new HashMap<>();
                valueMap.put("value", mv.getValue());
                valueMap.put("unit", mv.getUnit());
                meterValues.put(key, valueMap);
            });

            meterValuesWrapper.put("timestamp", timestamp);
            meterValuesWrapper.put("values", meterValues);
        }
        response.put("meterValues", meterValuesWrapper);

        return response;
    }

    @Operation(description = "Returns the latest transaction for a specific charger")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "No transactions found for the charger", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))}),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))})
    })
    @GetMapping(value = "/charger/{chargeBoxId}/latest")
    @ResponseBody
    public Map<String, Object> getLatestTransactionForCharger(@PathVariable("chargeBoxId") String chargeBoxId) {
        log.debug("Read request for latest transaction of charger: {}", chargeBoxId);

        TransactionQueryForm.ForApi params = new TransactionQueryForm.ForApi();
        params.setChargeBoxId(chargeBoxId);
        params.setReturnCSV(false);

        List<Transaction> transactions = transactionRepository.getTransactions(params);
        
        if (transactions.isEmpty()) {
            throw new BadRequestException("No transactions found for charger: " + chargeBoxId);
        }
        
        // Get the most recent transaction
        Transaction latestTransaction = transactions.get(0);
        TransactionDetails details = transactionRepository.getDetails(latestTransaction.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("id", latestTransaction.getId());
        response.put("chargeBoxId", chargeBoxId);
        response.put("connectorId", latestTransaction.getConnectorId());
        response.put("startTimestamp", latestTransaction.getStartTimestamp());
        response.put("startValue", latestTransaction.getStartValue());
        response.put("timestamp", DateTime.now());

        if (details != null) {
            response.put("connectorStatus", details.getConnectorStatus());
            response.put("chargingTime", details.getChargingTime());

            // Group meter values by measurand type and get the latest for each
            Map<String, TransactionDetails.MeterValues> latestByType = new HashMap<>();
            if (details.getValues() != null) {
                details.getValues().forEach(mv -> {
                    String measurand = mv.getMeasurand().toLowerCase();
                    String key;
                    if (measurand.contains("energy") && measurand.contains("active") && measurand.contains("import")) {
                        key = "energy.active.import.register";
                    } else if (measurand.contains("temperature")) {
                        key = "temperature";
                    } else if (measurand.contains("current") && measurand.contains("import")) {
                        key = "current.import";
                    } else if (measurand.contains("power") && measurand.contains("active") && measurand.contains("import")) {
                        key = "power.active.import";
                    } else if (measurand.contains("voltage")) {
                        key = "voltage";
                    } else {
                        return;
                    }

                    // Update if this is the first value or if this value is more recent
                    if (!latestByType.containsKey(key) || 
                        mv.getValueTimestamp().isAfter(latestByType.get(key).getValueTimestamp())) {
                        latestByType.put(key, mv);
                    }
                });

                // Find the latest timestamp across all selected meter values
                Optional<DateTime> latestTimestamp = latestByType.values().stream()
                    .map(TransactionDetails.MeterValues::getValueTimestamp)
                    .max(DateTime::compareTo);

                Map<String, Object> meterValuesWrapper = new HashMap<>();
                if (latestTimestamp.isPresent()) {
                    DateTime timestamp = latestTimestamp.get();
                    Map<String, Map<String, String>> meterValues = new HashMap<>();
                    
                    latestByType.forEach((key, mv) -> {
                        Map<String, String> valueMap = new HashMap<>();
                        valueMap.put("value", mv.getValue());
                        valueMap.put("unit", mv.getUnit());
                        meterValues.put(key, valueMap);
                    });

                    meterValuesWrapper.put("timestamp", timestamp);
                    meterValuesWrapper.put("values", meterValues);
                }
                response.put("meterValues", meterValuesWrapper);
            }
        }

        return response;
    }
}
