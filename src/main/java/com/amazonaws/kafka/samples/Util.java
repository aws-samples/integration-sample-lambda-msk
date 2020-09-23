package com.amazonaws.kafka.samples;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

class Util {

    private static final Logger logger = LogManager.getLogger(Util.class);
    static void logEnvironment(Object event, Context context, Gson gson)
    {
        // log execution details
        logger.info("ENVIRONMENT VARIABLES: {} \n", gson.toJson(System.getenv()));
        logger.info("CONTEXT: {} \n", gson.toJson(context));
        // log event details
        logger.info("EVENT: {} \n", gson.toJson(event));
        logger.info("EVENT TYPE: {} \n", event.getClass().toString());
    }

    static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    static long getExpBackoffInterval(int retries, boolean exponentialBackoff){
        int seed = 23;
        if (exponentialBackoff){
            return (2*(Double.valueOf(Math.pow(seed, retries)).longValue()))/1000;
        }
        else
            return 1L;
    }
}
