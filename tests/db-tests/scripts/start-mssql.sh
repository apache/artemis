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

export LICENSE="ACCEPT_EULA=N"

if [ $# -ge 1 ]; then
    # Check if the first argument is --accept-license
    if [ "$1" == "--accept-license" ]; then
        ./print-license.sh "SQL Server" "Microsoft"
        export LICENSE="ACCEPT_EULA=Y"
    else
        echo "Warning: you must accept the Microsoft license. Run ./logs-mssql.sh to check the log output."
    fi
else
    echo "Warning: you must accept the Microsoft license. Run ./logs-mssql.sh to check the log output."
fi

./stop-mssql.sh

$CONTAINER_COMMAND run -d --name mssql-artemis-test -e "$LICENSE" -e "MSSQL_SA_PASSWORD=ActiveMQ*Artemis" -p 1433:1433 mcr.microsoft.com/mssql/server:2019-latest
