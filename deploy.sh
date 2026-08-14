#!/bin/bash
if [ "$#" -lt  "3" ]
   then
     echo "Not enough arguments supplied"
     echo "Usage: deploy.sh <VPC Stack name> <Kafka Client Stack Name> <MSK Stack Name>"
     echo "Usage with Optional Parameters: deploy.sh <VPC Stack name> <Kafka Client Stack Name> <MSK Stack Name> <BatchSize> <Lambda Retries>"
     exit 1
fi

source /home/ec2-user/.bash_profile
sh ./create-bucket.sh
if [ $? -eq 0 ]
then
  echo "S3 bucket for artifacts created."
else
  echo "S3 bucket for artifacts could not be created. Exiting.."
  exit 1
fi

echo "Building Java artifact ..."
mvn clean package

if [ $? -eq 0 ]
then
  echo "jar successfully built."
else
  echo "jar build failed. Exiting."
  exit 1
fi

ARTIFACT_BUCKET=$(cat /tmp/bucket-name.txt)
TEMPLATE=template.yml
VPC_STACK=$1
KAFKA_CLIENT_STACK=$2
MSK_STACK=$3

# The function produces to a Kafka topic, so it has to run inside the VPC to
# reach the brokers. Discover where the cluster lives rather than asking for it:
# the cluster already knows its own subnets and security groups.
echo "Discovering MSK cluster networking ..."
CLUSTER_ARN=$(aws cloudformation describe-stacks --stack-name $MSK_STACK \
  --query "Stacks[0].Outputs[?OutputKey=='MSKClusterArn'].OutputValue" --output text)
if [ -z "$CLUSTER_ARN" ] || [ "$CLUSTER_ARN" = "None" ]; then
  echo "Could not read the MSKClusterArn output from stack $MSK_STACK. Exiting."
  exit 1
fi
LAMBDA_SUBNETS=$(aws kafka describe-cluster-v2 --cluster-arn $CLUSTER_ARN \
  --query 'ClusterInfo.Provisioned.BrokerNodeGroupInfo.ClientSubnets' --output text | tr '\t' ',')
LAMBDA_SGS=$(aws kafka describe-cluster-v2 --cluster-arn $CLUSTER_ARN \
  --query 'ClusterInfo.Provisioned.BrokerNodeGroupInfo.SecurityGroups' --output text | tr '\t' ',')
if [ -z "$LAMBDA_SUBNETS" ] || [ -z "$LAMBDA_SGS" ]; then
  echo "Could not determine the cluster's subnets or security groups. Exiting."
  exit 1
fi
echo "Function subnets:        $LAMBDA_SUBNETS"
echo "Function security groups: $LAMBDA_SGS"
#BATCH_SIZE=
#LAMBDA_RETRIES=

aws cloudformation package --template-file $TEMPLATE --s3-bucket $ARTIFACT_BUCKET --output-template-file out.yml

if [ $? -eq 0 ]
then
  echo "CloudFormation package build successful."
else
  echo "CloudFormation package build unsuccessful.Exiting."
  exit 1
fi

#use appropriate command based on input parameters
aws cloudformation deploy --template-file out.yml --stack-name MSKToS3 --s3-bucket $ARTIFACT_BUCKET --capabilities CAPABILITY_NAMED_IAM --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK LambdaSubnetIds=$LAMBDA_SUBNETS LambdaSecurityGroupIds=$LAMBDA_SGS
#aws cloudformation deploy --template-file out.yml --stack-name MSKToS3 --s3-bucket $ARTIFACT_BUCKET --capabilities CAPABILITY_NAMED_IAM --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK BatchSize=$BATCH_SIZE
#aws cloudformation deploy --template-file out.yml --stack-name MSKToS3 --s3-bucket $ARTIFACT_BUCKET --capabilities CAPABILITY_NAMED_IAM --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK LambdaRetries=$LAMBDA_RETRIES
#aws cloudformation deploy --template-file out.yml --stack-name MSKToS3 --s3-bucket $ARTIFACT_BUCKET --capabilities CAPABILITY_NAMED_IAM --parameter-overrides VPCStack=$VPC_STACK BastionStack=$KAFKA_CLIENT_STACK MSKStack=$MSK_STACK BatchSize=$BATCH_SIZE LambdaRetries=$LAMBDA_RETRIES