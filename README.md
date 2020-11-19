This is a sample Lambda function written in Java that illustrates the integration between Amazon MSK and AWS Lambda and 
is intended to be used with the [Amazon MSK AWS Lambda Integration Lab](https://amazonmsk-labs.workshop.aws/en/msklambda.html).
The Lambda function processes Clickstream events from an Amazon MSK Apache Kafka topic and batches and sends the events to 
Amazon S3 for backup and long term storage.

## Install

   * Clone the repository

         git clone https://github.com/aws-samples/integration-sample-lambda-msk.git
         cd integration-sample-lambda-msk
    
   * Run the deploy.sh script. It is intended to run on linux and Mac. The script does the following:
   *Note: The script requires some parameters. See [Setup Lambda](https://amazonmsk-labs.workshop.aws/en/msklambda/setuplambda.html) for details.*
    
       1. It builds a jar file for the Lambda function.
       2. It creates an Amazon S3 bucket to be used for uploaded artifacts with a random prefix in its name.
       3. It uses CloudFormation to build the Lambda function and package its resources.
       4. It deploys the sam template and creates a CloudFormation stack with multiple resources. The resources include:
           * An IAM policy to be used by the Lambda function.
           * An IAM role with the policy to be used by the Lambda function.
           * An IAM role to be used by Amazon Kinesis Data Firehose.
           * A Kinesis Data Firehose delivery stream.
           * The Lambda function which will process records from a topic in Amazon MSK and send to Kinesis Data Firehose.
           * An EventSourceMapping mapping the Lambda function to the Amazon MSK Apache Kafka topic.
    
    