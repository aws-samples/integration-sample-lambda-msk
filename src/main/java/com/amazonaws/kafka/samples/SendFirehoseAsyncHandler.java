package com.amazonaws.kafka.samples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponseEntry;
import software.amazon.awssdk.services.firehose.model.Record;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SendFirehoseAsyncHandler {

    private final int retries;
    private final String requestId; 
    private final long batchNumber;
    private static final Logger logger = LoggerFactory.getLogger(SendFirehoseAsyncHandler.class);

    SendFirehoseAsyncHandler(int retries, String requestId, long batchNumber) {
        this.retries = retries;
        this.requestId = requestId;
        this.batchNumber = batchNumber;
    }

    public void handleError(Throwable e) {
        logger.error("{} - Could not send batch with Request ID {} with batch number {} to Kinesis Data Firehose. \n {} \n", 
            Thread.currentThread().getName(), requestId, batchNumber, stackTrace(e));
    }

    public void handleSuccess(PutRecordBatchRequest putRecordBatchRequest, PutRecordBatchResponse putRecordBatchResponse) {
        if (putRecordBatchResponse.failedPutCount() > 0) {
            if (retries + 1 < Integer.parseInt(System.getenv("RETRIES"))) {
                List<Record> resendFirehoseBatch = new ArrayList<>();
                int index = 0;
                for (PutRecordBatchResponseEntry i : putRecordBatchResponse.requestResponses()) {
                    if (i.errorCode() != null) {
                        if (i.errorCode().equals("ServiceUnavailableException")) {
                            resendFirehoseBatch.add(putRecordBatchRequest.records().get(index));
                        }
                    }
                    index++;
                }

                logger.info("{} - Retrying send for Request ID {} with batch number {} with {} failed records... \n",
                    Thread.currentThread().getName(), requestId, batchNumber, resendFirehoseBatch.size());
                long sleepFor = getExpBackoffInterval(retries + 1, true);
                logger.info("Sleeping for {} seconds before retrying. \n", sleepFor);
                try {
                    TimeUnit.SECONDS.sleep(sleepFor);
                } catch (InterruptedException e) {
                    stackTrace(e);
                }
                logger.debug("{} - Resending records for Request ID {} with batch number {} - {} \n",
                    Thread.currentThread().getName(), requestId, batchNumber, resendFirehoseBatch);
                SendKinesisDataFirehose.sendFirehoseBatch(resendFirehoseBatch, retries + 1, requestId, batchNumber);
            } else {
                logger.info("{} - All Retries exhausted for Request ID {} with batch number {}. Could not send {} failed records. \n",
                    Thread.currentThread().getName(), requestId, batchNumber, putRecordBatchResponse.failedPutCount());
            }

        } else {
            logger.info("{} - Successfully sent {} records for Request ID {} with batch number {} \n",
                Thread.currentThread().getName(), putRecordBatchResponse.requestResponses().size(), requestId, batchNumber);
        }
    }

    static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    static long getExpBackoffInterval(int retries, boolean exponentialBackoff) {
        int seed = 23;
        if (exponentialBackoff) {
            return (2 * (Double.valueOf(Math.pow(seed, retries)).longValue())) / 1000;
        } else {
            return 1L;
        }
    }
}
