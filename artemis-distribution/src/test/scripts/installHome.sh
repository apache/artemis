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

# Setting the script to fail if anything goes wrong
set -e

export CURRENT_DIR=`pwd`

export LOCAL_USED=`ls ../../../target/apache-artemis-*bin.tar.gz`
echo Unziping $LOCAL_USED

if [ -z "$LOCAL_USED" ] ; then
   echo "Couldn't find distribution file"
   exit -1
fi

echo $LOCAL_USED

tar -zxf $LOCAL_USED -C "$TEST_TARGET"
cd "$TEST_TARGET"
export ARTEMIS_HOME="`pwd`/`ls`"
echo home is $ARTEMIS_HOME

cd $CURRENT_DIR
