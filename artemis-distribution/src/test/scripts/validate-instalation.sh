#!/usr/bin/env sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# This script will validate the distribution works with folders with spaces on Linux machines

echo validating installation on $1
rm -rf target
mkdir target
mkdir target/"$1"

if [ -z "$2" ]
  then
    export ARTEMIS_ROLE=amq
  else
    export ARTEMIS_ROLE=$2
fi

echo with the role $ARTEMIS_ROLE

# Setting the script to fail if anything goes wrong
set -e


export TEST_TARGET="./target/$1"

. ./installHome.sh


export ARTEMIS_INSTANCE="$CURRENT_DIR/target/$1/artemis_instance"
echo home used is $ARTEMIS_HOME
echo artemis instance is $ARTEMIS_HOME


cd "$ARTEMIS_HOME/bin"
./artemis create --silent --force --role "$ARTEMIS_ROLE" "$ARTEMIS_INSTANCE" --user admin --password admin

cd "$ARTEMIS_INSTANCE/bin"
pwd

./artemis run &

sleep 5

./artemis producer
./artemis consumer --receive-timeout 10000 --break-on-null

./artemis stop

sleep 5
./artemis data print > data.log
./artemis data compact
./artemis data exp


./artemis-service start

sleep 5

./artemis producer
./artemis consumer --receive-timeout 10000 --break-on-null

export HTTP_CODE=$(curl -H "Origin:http://localhost" -u admin:admin --write-out '%{http_code}' --silent --output /dev/null http://localhost:8161/console/jolokia/read/org.apache.activemq.artemis:broker=%220.0.0.0%22/Version)

if [ "$HTTP_CODE" -ne 200 ]
  then
    echo "Artemis Jolokia REST API check failed: " $HTTP_CODE
  else
    echo "Artemis Jolokia REST API check passed"
fi

./artemis-service stop

cd $CURRENT_DIR
rm -rf target
