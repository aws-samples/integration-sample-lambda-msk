package com.amazonaws.kafka.samples;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseAsync;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseAsyncClientBuilder;
import com.amazonaws.services.kinesisfirehose.model.PutRecordBatchRequest;
import com.amazonaws.services.kinesisfirehose.model.Record;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


class SendKinesisDataFirehose {
    private static final Logger logger = LogManager.getLogger(SendKinesisDataFirehose.class);
    private List<Record> firehoseBatch = new ArrayList<>();
    private Integer batchSize = 0;
    static AtomicInteger batchNumber = new AtomicInteger(0);
    private static final AmazonKinesisFirehoseAsync firehoseClient = getFirehoseAsyncClient();

    private static AmazonKinesisFirehoseAsync getFirehoseAsyncClient(){
        AmazonKinesisFirehoseAsyncClientBuilder amazonKinesisFirehoseAsyncClientBuilder = AmazonKinesisFirehoseAsyncClientBuilder.standard()
                .withRegion(System.getenv("AWS_REGION"))
                .withCredentials(new DefaultAWSCredentialsProviderChain());
        return amazonKinesisFirehoseAsyncClientBuilder.build();
    }

    static void sendFirehoseBatch(List<Record> firehoseBatch, int retries, String requestId, long batchNumber) {
        PutRecordBatchRequest putRecordBatchRequest = new PutRecordBatchRequest()
                .withDeliveryStreamName(System.getenv("DELIVERY_STREAM_NAME"))
                .withRecords(firehoseBatch);
        logger.info("{} - Sending batch with Request ID {} with batch number {} with {} records to Kinesis Data Firehose. \n", Thread.currentThread().getName(), requestId, batchNumber, firehoseBatch.size());
        firehoseClient.putRecordBatchAsync(putRecordBatchRequest, new SendFirehoseAsyncHandler(retries, requestId, batchNumber));

    }

    void addFirehoseRecordToBatch(String firehoseJsonRecord, String requestId) {
        Record firehoseRecord = new Record().withData(ByteBuffer.wrap(firehoseJsonRecord.getBytes()));
        if (firehoseBatch.size() + 1 > 500 || batchSize + firehoseJsonRecord.getBytes().length > 4194304){
            sendFirehoseBatch(firehoseBatch, 0, requestId, batchNumber.incrementAndGet());
            firehoseBatch.clear();
            batchSize = 0;
        }
        firehoseBatch.add(firehoseRecord);
        batchSize+= firehoseJsonRecord.getBytes().length;
    }

    List<Record> getFirehoseBatch(){
        return firehoseBatch;
    }
}
