package com.amazonaws.kafka.samples;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.Record;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

class SendKinesisDataFirehose {
    private static final Logger logger = LoggerFactory.getLogger(SendKinesisDataFirehose.class);
    private List<Record> firehoseBatch = new ArrayList<>();
    private Integer batchSize = 0;
    private static Integer MAX_SIZE = 500;
    static AtomicInteger batchNumber = new AtomicInteger(0);
    private static final FirehoseAsyncClient firehoseClient = getFirehoseAsyncClient();

    private static FirehoseAsyncClient getFirehoseAsyncClient() {
        return FirehoseAsyncClient.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(300L)) 
                    .apiCallAttemptTimeout(Duration.ofSeconds(300L))
                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(10).build())
                    .build())
                .httpClientBuilder(
                    NettyNioAsyncHttpClient.builder()
                    .maxConcurrency(100)
                    .connectionTimeout(Duration.ofSeconds(60))
                    .maxPendingConnectionAcquires(1000)
                    .connectionAcquisitionTimeout(Duration.ofSeconds(120))
                    .connectionTimeToLive(Duration.ofMinutes(5))
                )
                .build();
    }

    static void sendFirehoseBatch(List<Record> firehoseBatch, int retries, String requestId, long batchNumber) {
        PutRecordBatchRequest putRecordBatchRequest = PutRecordBatchRequest.builder()
                .deliveryStreamName(System.getenv("DELIVERY_STREAM_NAME"))
                .records(firehoseBatch)
                .build();
        logger.info("{} - Sending batch with Request ID {} with batch number {} with {} records to Kinesis Data Firehose. \n", Thread.currentThread().getName(), requestId, batchNumber, firehoseBatch.size());
        firehoseClient.putRecordBatch(putRecordBatchRequest)
                .whenComplete((response, error) -> {
                    SendFirehoseAsyncHandler handler = new SendFirehoseAsyncHandler(retries, requestId, batchNumber);
                    if (error != null) {
                        handler.handleError(error);
                    } else {
                        handler.handleSuccess(putRecordBatchRequest, response);
                    }
                });
    }

    void addFirehoseRecordToBatch(String firehoseJsonRecord, String requestId) {
        Record firehoseRecord = Record.builder()
                .data(SdkBytes.fromByteArray((firehoseJsonRecord.getBytes())))
                .build();
        if (firehoseBatch.size() + 1 > MAX_SIZE || batchSize + firehoseJsonRecord.getBytes().length > 4194304) {
            sendFirehoseBatch(firehoseBatch, 0, requestId, batchNumber.incrementAndGet());
            firehoseBatch.clear();
            batchSize = 0;
        }
        firehoseBatch.add(firehoseRecord);
        batchSize += firehoseJsonRecord.getBytes().length;
    }

    List<Record> getFirehoseBatch() {
        return firehoseBatch;
    }
}
