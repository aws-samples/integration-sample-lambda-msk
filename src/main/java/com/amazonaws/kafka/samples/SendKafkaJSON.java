package com.amazonaws.kafka.samples;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Produces plain JSON records to a Kafka topic for downstream consumption
 * by services that require JSON input (e.g., MSK streaming tables for Apache Iceberg).
 *
 * Enabled by setting the OUTPUT_TOPIC and BOOTSTRAP_SERVERS environment variables.
 * Uses IAM authentication to connect to the MSK cluster.
 */
class SendKafkaJSON {
    private static final Logger logger = LoggerFactory.getLogger(SendKafkaJSON.class);
    private static volatile KafkaProducer<String, String> producer;
    private static final String OUTPUT_TOPIC = System.getenv("OUTPUT_TOPIC");
    private static final String BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS");

    static boolean isEnabled() {
        return OUTPUT_TOPIC != null && !OUTPUT_TOPIC.isEmpty()
            && BOOTSTRAP_SERVERS != null && !BOOTSTRAP_SERVERS.isEmpty();
    }

    private static synchronized KafkaProducer<String, String> getProducer() {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.RETRIES_CONFIG, 3);
            props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
            props.put("security.protocol", "SASL_SSL");
            props.put("sasl.mechanism", "AWS_MSK_IAM");
            props.put("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            props.put("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
            producer = new KafkaProducer<>(props);
            logger.info("Kafka JSON producer initialized for topic: {}", OUTPUT_TOPIC);
        }
        return producer;
    }

    static void send(String jsonRecord) {
        getProducer().send(new ProducerRecord<>(OUTPUT_TOPIC, jsonRecord));
    }

    static void flush() {
        if (producer != null) {
            producer.flush();
        }
    }
}
