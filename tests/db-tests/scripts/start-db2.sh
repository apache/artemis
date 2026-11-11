#!/bin/sh
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

source ./container-define.sh


# NOTE: at the time this script was written podman had an issue starting DB2 without a folder specified. Docker ran it without any problems.
#       If you must use podman you could specify a data folder and it should work fine

# As documented on https://www.ibm.com/docs/en/db2/11.5?topic=system-linux

export LICENSE=reject

if [ $# -ge 1 ]; then
    # Check if the first argument is --accept-license
    if [ "$1" == "--accept-license" ]; then
        ./print-license.sh "DB2" "IBM"
        export LICENSE=accept
    else
        echo "Warning: you must accept the DB2 license. Run ./logs-db2.sh to check the log output."
        echo "Usage: $0 --accept-license <folder_data>"
    fi
else
    echo "Warning: you must accept the DB2 license. Run ./logs-db2.sh to check the log output."
    echo "Usage: $0 --accept-license <folder_data>"
fi

if [ $# -ne 2 ]; then
    echo "NO_DATA has been specified. not using a data folder"
    data_argument=""
    folder_data="NO_DATA"
else
   folder_data=$2
   data_argument="-v $folder_data:/database:Z"
fi

./stop-db2.sh

if [ ! -d "$folder_data" ] && [ "$folder_data" != "NO_DATA" ]; then
    mkdir "$folder_data"
    chmod 777 $folder_data
    echo "Folder '$folder_data' created."
fi

$CONTAINER_COMMAND run -d -h db2-artemis-test --name db2-artemis-test --privileged=true -p 50000:50000 -eLICENSE=$LICENSE --env-file db2.env $data_argument icr.io/db2_community/db2