package com.amazonaws.kafka.samples;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KafkaEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HandlerMSK implements RequestHandler<KafkaEvent, String> {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String handleRequest(KafkaEvent kafkaEvent, Context context) {
        String response = "200 OK";
        Util.logEnvironment(kafkaEvent, context, gson);
        new ProcessRecords().processRecords(kafkaEvent, context.getAwsRequestId());
        return response;
    }
}
