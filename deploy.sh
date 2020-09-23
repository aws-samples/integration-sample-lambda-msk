#!/bin/bash
if [ "$#" -lt  "3" ]
   then
     echo "Not enough arguments supplied"
     echo "Usage: deploy.sh <VPC Stack name> <Kafka Client Stack Name> <MSK Stack Name>"
     echo "Usage with Optional Parameters: deploy.sh <VPC Stack name> <Kafka Client Stack Name> <MSK Stack Name> <BatchSize> <Lambda Retries>"
     exit 1
fi

. ./install-sam.sh

if [ $? -eq 0 ]
then
  echo "aws sam installed."
else
  echo "sam could not be installed. Exiting.."
  exit 1
fi
source /home/ec2-user/.bash_profile
. ./create-bucket.sh
if [ $? -eq 0 ]
then
  echo "S3 bucket for sam artifacts created."
else
  echo "S3 bucket for sam artifacts could not be created. Exiting.."
  exit 1
fi

ARTIFACT_BUCKET=$(cat /tmp/bucket-name.txt)
TEMPLATE=template.yml
AWS_SAM_BUILD_DIR=/tmp/aws-sam/build
VPC_STACK=$1
KAFKA_CLIENT_STACK=$2
MSK_STACK=$3
#BATCH_SIZE=
#LAMBDA_RETRIES=

sam build ProcessMSKfunction --template $TEMPLATE --build-dir $AWS_SAM_BUILD_DIR

if [ $? -eq 0 ]
then
  echo "sam build successful."
else
  echo "sam build unsuccessful. Exiting.."
  exit 1
fi

#use appropriate command based on input parameters
sam deploy --template-file $AWS_SAM_BUILD_DIR/template.yaml --stack-name MSKToS3 --capabilities CAPABILITY_NAMED_IAM --s3-bucket $ARTIFACT_BUCKET --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK
#sam deploy --template-file /tmp/aws-sam/build/template.yaml --stack-name MSKToS3 --capabilities CAPABILITY_NAMED_IAM --s3-bucket $ARTIFACT_BUCKET --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK BatchSize=$BATCH_SIZE
#sam deploy --template-file /tmp/aws-sam/build/template.yaml --stack-name MSKToS3 --capabilities CAPABILITY_NAMED_IAM --s3-bucket $ARTIFACT_BUCKET --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK LambdaRetries=$LAMBDA_RETRIES
#sam deploy --template-file /tmp/aws-sam/build/template.yaml --stack-name MSKToS3 --capabilities CAPABILITY_NAMED_IAM --s3-bucket $ARTIFACT_BUCKET --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK BatchSize=$BATCH_SIZE LambdaRetries=$LAMBDA_RETRIES