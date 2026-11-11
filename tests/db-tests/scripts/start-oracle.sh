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

if [ $# -ne 1 ]; then
    echo "Usage: $0 <folder_data>"
    echo "       setting folder_data as NO_DATA by default"
    folder_data="NO_DATA"
else
    folder_data="$1"
fi

./stop-oracle.sh

./print-license.sh Oracle Oracle

if [ "$folder_data" = "NO_DATA" ]; then
    echo "NO_DATA has been specified. not using a data folder"
    data_argument=""
else
   data_argument="-v $folder_data:/opt/oracle/oradata:Z"
fi

if [ ! -d "$folder_data" ] && [ "$folder_data" != "NO_DATA" ]; then
    mkdir "$folder_data"
    chmod 777 $folder_data
    echo "Folder '$folder_data' created."
fi

$CONTAINER_COMMAND run -d --name oracle-artemis-test -p 1521:1521 $data_argument -e ORACLE_PWD=artemis container-registry.oracle.com/database/free:latest
