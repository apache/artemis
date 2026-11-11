@echo off
rem Licensed to the Apache Software Foundation (ASF) under one
rem or more contributor license agreements.  See the NOTICE file
rem distributed with this work for additional information
rem regarding copyright ownership.  The ASF licenses this file
rem to you under the Apache License, Version 2.0 (the
rem "License"); you may not use this file except in compliance
rem with the License.  You may obtain a copy of the License at
rem
rem   http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing,
rem software distributed under the License is distributed on an
rem "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
rem KIND, either express or implied.  See the License for the
rem specific language governing permissions and limitations
rem under the License.

set ARTEMIS_HOME="must-change"
set ARTEMIS_INSTANCE="${project.basedir}/target/classes/servers/windowsUpgrade"
set ARTEMIS_DATA_DIR="${project.basedir}/target/classes/servers/windowsUpgrade/data-custom"
set ARTEMIS_ETC_DIR="${project.basedir}/target/classes/servers/windowsUpgradeETC"
set ARTEMIS_OOME_DUMP="${project.basedir}/target/classes/servers/windowsUpgrade/log/oom_dump.hprof"


rem The logging config will need an URI
rem this will be encoded in case you use spaces or special characters
rem on your directory structure
set ARTEMIS_INSTANCE_URI="file:${project.basedir}/target/classes/servers/windowsUpgrade/"
set ARTEMIS_INSTANCE_ETC_URI="file:${project.basedir}/target/classes/servers/windowsUpgradeETC/"

rem Cluster Properties: Used to pass arguments to ActiveMQ Artemis which can be referenced in broker.xml
rem set ARTEMIS_CLUSTER_PROPS=-Dactivemq.remoting.default.port=61617 -Dactivemq.remoting.amqp.port=5673 -Dactivemq.remoting.stomp.port=61614 -Dactivemq.remoting.hornetq.port=5446

rem Java Opts
IF "%JAVA_ARGS%"=="" (set JAVA_ARGS= -XX:AutoBoxCacheMax=20000 -XX:+PrintClassHistogram  -XX:+UseG1GC -XX:+UseStringDeduplication -Xms512M -Xmx1G -Djava.security.auth.login.config=%ARTEMIS_ETC_DIR%\login.config -Dhawtio.disableProxy=true -Dhawtio.offline=true -Dhawtio.realm=activemq -Dhawtio.role=amq -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal -Djolokia.policyLocation=%ARTEMIS_INSTANCE_ETC_URI%\jolokia-access.xml -Dartemis.instance=%ARTEMIS_INSTANCE%)

rem Logs Safepoints JVM pauses: Uncomment to enable them
rem In addition to the traditional GC logs you could enable some JVM flags to know any meaningful and "hidden" pause that could
rem affect the latencies of the services delivered by the broker, including those that are not reported by the classic GC logs
rem and dependent by JVM background work (eg method deoptimizations, lock unbiasing, JNI, counted loops and obviously GC activity).
rem Replace "all_pauses.log" with the file name you want to log to.
rem set JAVA_ARGS=%JAVA_ARGS% -XX:+PrintSafepointStatistics -XX:PrintSafepointStatisticsCount=1 -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime -XX:+LogVMOutput -XX:LogFile=all_pauses.log

rem Enables the dumping of the java heap when a java.lang.OutOfMemoryError exception is thrown.
rem set JAVA_ARGS=%JAVA_ARGS% -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=%ARTEMIS_OOME_DUMP%

rem Only enable debug options for the 'run' command
rem Uncomment to enable remote debugging
rem if "%1"=="run" set DEBUG_ARGS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005