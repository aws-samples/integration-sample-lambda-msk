#!/bin/bash
which sam
if [ $? -eq 0 ]
then
 echo "aws sam installed"
else
 pip install aws-sam-cli
 if [ $? -eq 0 ]
  then
   echo "aws-sam-cli installed"
  else
   echo "aws-sam-cli could not be installed. Exiting."
   exit 1
  fi
fi