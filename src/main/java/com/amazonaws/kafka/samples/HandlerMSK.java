package com.amazonaws.kafka.samples;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.lambda.powertools.kafka.Deserialization;
import software.amazon.lambda.powertools.kafka.DeserializationType;
import software.amazon.lambda.powertools.logging.Logging;

public class HandlerMSK implements RequestHandler<ConsumerRecords<String, String>, String> {
    
    private static final Logger logger = LoggerFactory.getLogger(HandlerMSK.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private void processRecords(ConsumerRecords<String, String> records, String requestId) {
        SendKinesisDataFirehose sendKinesisDataFirehose = new SendKinesisDataFirehose();
        
        for (ConsumerRecord<String, String> record : records) {
            // Transform the record payload
            String transformedRecord = transformPayload(record.value(), requestId);
            sendKinesisDataFirehose.addFirehoseRecordToBatch(transformedRecord.concat("\n"), requestId);
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
            mutableRoot.put("processed_timestamp", System.currentTimeMillis());
            
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
