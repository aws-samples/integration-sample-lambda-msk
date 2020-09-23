package com.amazonaws.kafka.samples;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.kinesisfirehose.model.PutRecordBatchRequest;
import com.amazonaws.services.kinesisfirehose.model.PutRecordBatchResponseEntry;
import com.amazonaws.services.kinesisfirehose.model.PutRecordBatchResult;
import com.amazonaws.services.kinesisfirehose.model.Record;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SendFirehoseAsyncHandler  implements AsyncHandler<PutRecordBatchRequest, PutRecordBatchResult> {

    private final int retries;
    private final String requestId;
    private final long batchNumber;
    private static final Logger logger = LogManager.getLogger(SendFirehoseAsyncHandler.class);

    SendFirehoseAsyncHandler(int retries, String requestId, long batchNumber) {
        this.retries = retries;
        this.requestId = requestId;
        this.batchNumber = batchNumber;
    }

    @Override
    public void onError(Exception e) {
        logger.error("{} - Could not send batch with Request ID {} with batch number {} to Kinesis Data Firehose. \n {} \n", Thread.currentThread().getName(), requestId, batchNumber, Util.stackTrace(e));
    }

    @Override
    public void onSuccess(PutRecordBatchRequest putRecordBatchRequest, PutRecordBatchResult putRecordBatchResult) {

        if (putRecordBatchResult.getFailedPutCount() > 0){
            if (retries + 1 <  Integer.parseInt(System.getenv("RETRIES"))) {
                List<Record> resendFirehoseBatch = new ArrayList<>();
                int index = 0;
                for (PutRecordBatchResponseEntry i : putRecordBatchResult.getRequestResponses()) {
                    if (i.getErrorCode() != null){
                        if (i.getErrorCode().equals("ServiceUnavailableException")) {
                            resendFirehoseBatch.add(putRecordBatchRequest.getRecords().get(index));
                        }
                    }
                    index++;
                }

                logger.info("{} - Retrying send for Request ID {} with batch number {} with {} failed records... \n", Thread.currentThread().getName(), requestId, batchNumber, resendFirehoseBatch.size());
                long sleepFor = Util.getExpBackoffInterval(retries + 1, true);
                logger.info("Sleeping for {} seconds before retrying. \n", sleepFor);
                try {
                    TimeUnit.SECONDS.sleep(sleepFor);
                } catch (InterruptedException e) {
                    Util.stackTrace(e);
                }
                logger.debug("{} - Resending records for Request ID {} with batch number {} - {} \n", Thread.currentThread().getName(), requestId, batchNumber, resendFirehoseBatch);
                SendKinesisDataFirehose.sendFirehoseBatch(resendFirehoseBatch, retries + 1, requestId, batchNumber);
            } else {
                logger.info("{} - All Retries exhausted for Request ID {} with batch number {}. Could not send {} failed records. \n", Thread.currentThread().getName(), requestId, batchNumber, putRecordBatchResult.getFailedPutCount());
            }

        } else {
            logger.info("{} - Successfully sent {} records for Request ID {} with batch number {} \n", Thread.currentThread().getName(), putRecordBatchResult.getRequestResponses().size(), requestId, batchNumber);
        }
    }
}
