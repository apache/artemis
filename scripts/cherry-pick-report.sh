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

# Setting the script to fail if anything goes wrong
set -e

# set this to true if you want to query the JIRAs on the report
USE_REST=false


# use this script to create a report on cherry-picks between main and maintenance branches
# start this script within the checkout folder

if [ ! -d "./target" ]
then
   mkdir target
fi

cd target

if [ ! -d "./cherry-pick-report" ]
then
   mkdir cherry-pick-report
fi

cd cherry-pick-report

if [ -d "./jira-git-report" ]
then
   cd jira-git-report
   git pull --rebase origin master
else
   git clone git@github.com:rh-messaging/jira-git-report.git
   cd jira-git-report
fi

mvn compile assembly:single

java -jar ./target/jira-git-0.1.SNAPSHOT-jar-with-dependencies.jar artemis ../../.. ../cherry-pick-report.html  2.19.0 main ${USE_REST} 2.19.x 2.19.0

java -jar ./target/jira-git-0.1.SNAPSHOT-jar-with-dependencies.jar artemis ../../.. ../commit-report-2.19.x.html  2.19.0 2.19.x ${USE_REST}

echo "=============================================================================================================================="
echo " Report generated at ./target/cherry-pick-report/cherry-pick-report.html"
echo " Report generated at ./target/cherry-pick-report/commit-report-2.19.x.html"
echo "=============================================================================================================================="



