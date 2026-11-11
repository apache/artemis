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

# more information about this script at https://github.com/apache/activemq-artemis/blob/main/docs/hacking-guide/en/history.md

git remote add -f temp-hornetq https://github.com/hornetq/hornetq.git
git remote add -f temp-upstream https://github.com/apache/activemq-artemis.git

set -e
git fetch temp-hornetq
git fetch temp-upstream
git checkout temp-hornetq/apache-donation -B donation
git checkout temp-upstream/main -B main-donation
git rebase donation
git remote rm temp-hornetq
git remote rm temp-upstream
git branch -D donation
