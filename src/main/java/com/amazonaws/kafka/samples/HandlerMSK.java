package com.amazonaws.kafka.samples;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.lambda.powertools.kafka.Deserialization;
import software.amazon.lambda.powertools.kafka.DeserializationType;
import software.amazon.lambda.powertools.logging.Logging;

public class HandlerMSK implements RequestHandler<ConsumerRecords<String, String>, String> {
    
    private static final Logger logger = LoggerFactory.getLogger(HandlerMSK.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // RFC 3339 / ISO 8601 in UTC with millisecond precision. Consumers that
    // partition on time need a timestamp-typed column, not epoch milliseconds.
    private static final DateTimeFormatter ISO_8601_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    
    private void processRecords(ConsumerRecords<String, String> records, String requestId) {
        SendKinesisDataFirehose sendKinesisDataFirehose = new SendKinesisDataFirehose();
        boolean kafkaEnabled = SendKafkaJSON.isEnabled();
        
        for (ConsumerRecord<String, String> record : records) {
            // Transform the record payload
            String transformedRecord = transformPayload(record.value(), requestId);
            sendKinesisDataFirehose.addFirehoseRecordToBatch(transformedRecord.concat("\n"), requestId);
            
            // Also produce plain JSON to the output Kafka topic if configured
            if (kafkaEnabled) {
                SendKafkaJSON.send(transformedRecord);
            }
        }
        
        // Flush Kafka producer to ensure all records are sent
        if (kafkaEnabled) {
            SendKafkaJSON.flush();
        }
        
        SendKinesisDataFirehose.sendFirehoseBatch(sendKinesisDataFirehose.getFirehoseBatch(), 0, requestId, SendKinesisDataFirehose.batchNumber.incrementAndGet());
        SendKinesisDataFirehose.batchNumber.set(0);
    }
    
    /**
     * Transform the payload by adding enrichment fields
     * @param jsonRecord The original JSON record as a string
     * @param requestId The Lambda request ID for logging
     * @return The transformed JSON record with additional fields
     * 
     * This method can be extended to add various transformations such as:
     * - GeoIP lookups based on IP address fields
     * - Data validation and cleansing
     * - Field normalization and standardization
     * - Additional metadata enrichment
     * - Format conversions
     */
    private String transformPayload(String jsonRecord, String requestId) {
        try {
            // Parse the JSON record
            JsonNode rootNode = objectMapper.readTree(jsonRecord);
            
            // Add processing timestamp
            ObjectNode mutableRoot = (ObjectNode) rootNode;
            long processedTimestamp = System.currentTimeMillis();
            mutableRoot.put("processed_timestamp", processedTimestamp);

            // Add an ISO-8601 rendering of the event time. The source record
            // carries eventtimestamp as epoch milliseconds, which downstream
            // consumers cannot use as a time partition column: those require a
            // timestamp-typed value. Emitting both keeps the numeric field
            // available for arithmetic while giving consumers something they can
            // partition on directly.
            JsonNode eventTimestamp = mutableRoot.get("eventtimestamp");
            long eventMillis;
            if (eventTimestamp != null && eventTimestamp.canConvertToLong()) {
                eventMillis = eventTimestamp.asLong();
            } else {
                // The field has to be present on every record or time-partitioned
                // delivery fails for the whole batch, so fall back to processing
                // time rather than omitting it.
                logger.warn("Record for request {} has no usable eventtimestamp; using processing time for event_time", requestId);
                eventMillis = processedTimestamp;
            }
            mutableRoot.put("event_time", ISO_8601_UTC.format(Instant.ofEpochMilli(eventMillis)));

            // Additional transformations can be added here:
            // - GeoIP lookup: Extract IP field and add location data
            // - Data enrichment: Add computed fields or external data
            // - Validation: Check required fields and data formats
            
            return objectMapper.writeValueAsString(mutableRoot);
            
        } catch (Exception e) {
            logger.error("Error transforming payload for request {}: {}", requestId, e.getMessage());
            // Return original record if transformation fails
            return jsonRecord;
        }
    }

    @Override
    @Logging(logEvent = true)
    @Deserialization(type = DeserializationType.KAFKA_JSON)
    public String handleRequest(ConsumerRecords<String, String> records, Context context) {
        logger.info("Processing batch with {} records for Request ID {} \n", records.count(), context.getAwsRequestId());
        processRecords(records, context.getAwsRequestId());
        return "200 OK";
    }

}
