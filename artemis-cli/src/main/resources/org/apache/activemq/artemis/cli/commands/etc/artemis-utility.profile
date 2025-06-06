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

ARTEMIS_HOME='${artemis.home}'
ARTEMIS_INSTANCE='@artemis.instance@'
ARTEMIS_DATA_DIR='${artemis.instance.data}'

if [ -z "$LOGGING_ARGS" ]; then
    LOGGING_ARGS="-Dlog4j2.configurationFile=log4j2-utility.properties"
fi

if [ -z "$JAVA_ARGS" ]; then
    JAVA_ARGS="-Dlog4j2.disableJmx=true --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED ${java-utility-opts}"
fi

# Uncomment to enable remote debugging
# DEBUG_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
