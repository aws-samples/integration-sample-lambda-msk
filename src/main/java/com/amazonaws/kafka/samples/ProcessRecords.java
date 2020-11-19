package com.amazonaws.kafka.samples;

import com.amazonaws.services.lambda.runtime.events.KafkaEvent;
import com.amazonaws.services.schemaregistry.deserializers.avro.AWSKafkaAvroDeserializer;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import com.amazonaws.services.schemaregistry.utils.AvroRecordType;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import samples.clickstream.avro.ClickEvent;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProcessRecords {

    private static final Logger logger = LogManager.getLogger(ProcessRecords.class);

    private byte[] base64Decode(KafkaEvent.KafkaEventRecord kafkaEventRecord) {
        return Base64.getDecoder().decode(kafkaEventRecord.getValue().getBytes());
    }

    private long getKafkaEventRecordsSize(KafkaEvent kafkaEvent) {
        long size = 0L;
        for (Map.Entry<String, List<KafkaEvent.KafkaEventRecord>> kafkaEventEntry : kafkaEvent.getRecords().entrySet()) {
            size += kafkaEventEntry.getValue().size();
        }
        return size;
    }

    private Map<String, Object> getGSRConfigs() {
        Map<String, Object> gsrConfigs = new HashMap<>();
        gsrConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        gsrConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AWSKafkaAvroDeserializer.class.getName());
        gsrConfigs.put(AWSSchemaRegistryConstants.AWS_REGION, System.getenv("AWS_REGION"));
        gsrConfigs.put(AWSSchemaRegistryConstants.AVRO_RECORD_TYPE, AvroRecordType.SPECIFIC_RECORD.getName());
        if (System.getenv("SECONDARY_DESERIALIZER") != null) {
            if (Boolean.parseBoolean(System.getenv("SECONDARY_DESERIALIZER"))) {
                gsrConfigs.put(AWSSchemaRegistryConstants.SECONDARY_DESERIALIZER, KafkaAvroDeserializer.class.getName());
                gsrConfigs.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, System.getenv("SCHEMA_REGISTRY_URL"));
                gsrConfigs.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
            }
        }
        return gsrConfigs;
    }

    private Map<String, Object> getCSRConfigs() {
        Map<String, Object> csrConfigs = new HashMap<>();
        csrConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        csrConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        csrConfigs.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        return csrConfigs;
    }

    private void deserializeAddToFirehoseBatch(Deserializer deserializer, KafkaEvent kafkaEvent, String requestId, SendKinesisDataFirehose sendKinesisDataFirehose) {
        kafkaEvent.getRecords().forEach((key, value) -> value.forEach(v -> {

            ClickEvent clickEvent = null;
            boolean csr = false;

            if (System.getenv("CSR") != null) {
                if (Boolean.parseBoolean(System.getenv("CSR"))) {
                    csr = true;
                    try {
                        GenericRecord rec = (GenericRecord) deserializer.deserialize(v.getTopic(), base64Decode(v));
                        clickEvent = (ClickEvent) SpecificData.get().deepCopy(ClickEvent.SCHEMA$, rec);
                    } catch (Exception e) {
                        logger.error(Util.stackTrace(e));
                    }
                }
            }

            if (!csr) {
                clickEvent = (ClickEvent) deserializer.deserialize(v.getTopic(), base64Decode(v));
            }

            if (clickEvent != null)
                sendKinesisDataFirehose.addFirehoseRecordToBatch(clickEvent.toString(), requestId);
        }));
    }

    void processRecords(KafkaEvent kafkaEvent, String requestId) {
        logger.info("Processing batch with {} records for Request ID {} \n", getKafkaEventRecordsSize(kafkaEvent), requestId);
        SendKinesisDataFirehose sendKinesisDataFirehose = new SendKinesisDataFirehose();
        Deserializer deserializer = null;
        if (System.getenv("CSR") != null) {
            if (Boolean.parseBoolean(System.getenv("CSR"))) {
                SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(System.getenv("SCHEMA_REGISTRY_URL"), 10, getCSRConfigs());
                deserializer = new KafkaAvroDeserializer(schemaRegistryClient);
            }
        }
        if (deserializer == null) {
            deserializer = new AWSKafkaAvroDeserializer(getGSRConfigs());
        }

        deserializeAddToFirehoseBatch(deserializer, kafkaEvent, requestId, sendKinesisDataFirehose);
        SendKinesisDataFirehose.sendFirehoseBatch(sendKinesisDataFirehose.getFirehoseBatch(), 0, requestId, SendKinesisDataFirehose.batchNumber.incrementAndGet());
        SendKinesisDataFirehose.batchNumber.set(0);
    }
}
