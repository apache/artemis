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

set -e

if [ $# -ne 2 ]
  then
    echo "Please supply version and repository name as parameters, e.g. ./release-docker 2.30.0 apache"
    exit
fi

VERSION=$1
REPO=$2

cd ../artemis-docker
rm -Rf target/
./prepare-docker.sh --from-release --artemis-version ${VERSION}
cd target/artemis/${VERSION}
docker pull eclipse-temurin:25-jre-alpine
docker pull eclipse-temurin:25-jre
docker login
docker buildx build --platform linux/amd64,linux/arm64 -f ./docker/Dockerfile-alpine-25-jre -t ${REPO}/artemis:${VERSION}-alpine -t ${REPO}/artemis:latest-alpine --push .
docker buildx build --platform linux/amd64,linux/arm64 -f ./docker/Dockerfile-ubuntu-25-jre -t ${REPO}/artemis:${VERSION} -t ${REPO}/artemis:latest --push .
