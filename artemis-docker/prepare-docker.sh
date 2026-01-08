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

#This is a script to Prepare an artemis folder to generate the Release.


usage () {
  cat <<HERE

$@

Usage:
  # Prepare for build the Docker Image from the local distribution
  ./prepare-docker.sh --from-local-dist --local-dist-path {local-distribution-directory}

  # Prepare for build the Docker Image from the release version
  ./prepare-docker.sh --from-release --artemis-version {release-version}

  # Show the usage command
  ./prepare-docker.sh --help

Example:
  ./prepare-docker.sh --from-local-dist --local-dist-path ../artemis-distribution/target/apache-artemis-2.17.0-SNAPSHOT-bin/apache-artemis-2.17.0-SNAPSHOT
  ./prepare-docker.sh --from-release --artemis-version 2.16.0

HERE
  exit 1
}

next_step () {
  cat <<HERE

Well done! Now you can continue with building the Docker image:

  # Go to $ARTEMIS_DIST_DIR
  $ cd $ARTEMIS_DIST_DIR

  # For Ubuntu with full JDK 25
  $ docker build -f ./docker/Dockerfile-ubuntu-25 -t artemis-ubuntu .

  # For Ubuntu with just JRE 25
  $ docker build -f ./docker/Dockerfile-ubuntu-25-jre -t artemis-ubuntu-jre .

  # For Alpine with full JDK 25
  $ docker build -f ./docker/Dockerfile-alpine-25 -t artemis-alpine .

  # For Alpine with just JRE 25
  $ docker build -f ./docker/Dockerfile-alpine-25-jre -t artemis-alpine-jre .

  # Multi-platform for Ubuntu on Linux AMD64 & ARM64 with full JDK
  $ docker buildx build --platform linux/amd64,linux/arm64 --push -t {your-repository}/apache-artemis:{your-version} -f ./docker/Dockerfile-ubuntu-25 .

Note: -t artemis-alpine and -t artemis-ubuntu are just tag names for the purpose of this guide

For more info see readme.md

HERE
  exit 0
}

while [ "$#" -ge 1 ]
do
key="$1"
  case $key in
    --help)
    usage
    ;;
    --from-local-dist)
    FROM_LOCAL="true"
    ;;
    --from-release)
    FROM_RELEASE="true"
    ;;
    --local-dist-path)
    LOCAL_DIST_PATH="$2"
    shift
    ;;
    --artemis-version)
    ARTEMIS_VERSION="$2"
    shift
    ;;
    *)
    # unknown option
    usage "Unknown option"
    ;;
  esac
  shift
done

# BASE_TMPDIR must be contained within the working directory so it is part of the
# Docker context. (i.e. it can't be in /tmp)
BASE_TMPDIR="target/artemis"

if [ -n "${FROM_RELEASE}" ] && [ -z "${ARTEMIS_VERSION}" ]; then
  usage "You must specify the release version (e.g. --artemis-version 2.16.0)"
fi

if [ -n "${FROM_RELEASE}" ]; then
  ARTEMIS_DIST_DIR="${BASE_TMPDIR}/${ARTEMIS_VERSION}"

  # Prepare directory
  if [ ! -d "${ARTEMIS_DIST_DIR}" ]; then
    echo "Creating ${ARTEMIS_DIST_DIR}"
    mkdir -p "${ARTEMIS_DIST_DIR}"
  elif [ ! -z "$(find "${BASE_TMPDIR}" -name "${ARTEMIS_VERSION}" -type d -mmin +60)" ]; then
    echo "Cleaning up ${ARTEMIS_DIST_DIR}"
    rm -rf ${ARTEMIS_DIST_DIR}/*
  else
    echo "Using ${ARTEMIS_DIST_DIR}"
  fi

  # Check if the release is already available locally, if not try to download it
  if [ -z "$(ls -A ${ARTEMIS_DIST_DIR})" ]; then
    CDN="$(curl -s https://www.apache.org/dyn/closer.cgi\?preferred=true)activemq/activemq-artemis/${ARTEMIS_VERSION}/"
    ARCHIVE="https://archive.apache.org/dist/activemq/activemq-artemis/${ARTEMIS_VERSION}/"
    ARTEMIS_BASE_URL=${CDN}
    ARTEMIS_DIST_FILE_NAME="apache-artemis-${ARTEMIS_VERSION}-bin.tar.gz"
    CURL_OUTPUT="${ARTEMIS_DIST_DIR}/${ARTEMIS_DIST_FILE_NAME}"

    # Fallback to the Apache archive if the version doesn't exist on the CDN anymore
    if [ -z "$(curl -Is ${ARTEMIS_BASE_URL}${ARTEMIS_DIST_FILE_NAME} | head -n 1 | grep 200)" ]; then
      ARTEMIS_BASE_URL=${ARCHIVE}

      # If the archive also doesn't work then report the failure and abort
      if [ -z "$(curl -Is ${ARTEMIS_BASE_URL}${ARTEMIS_DIST_FILE_NAME} | head -n 1 | grep 200)" ]; then
        echo "Failed to find ${ARTEMIS_DIST_FILE_NAME}. Tried both ${CDN} and ${ARCHIVE}."
        exit 1
      fi
    fi

    echo "Downloading ${ARTEMIS_DIST_FILE_NAME} from ${ARTEMIS_BASE_URL}..."
    curl --progress-bar "${ARTEMIS_BASE_URL}${ARTEMIS_DIST_FILE_NAME}" --output "${CURL_OUTPUT}"

    echo "Expanding ${ARTEMIS_DIST_DIR}/${ARTEMIS_DIST_FILE_NAME}..."
    tar xzf "$CURL_OUTPUT" --directory "${ARTEMIS_DIST_DIR}" --strip 1

    echo "Removing ${ARTEMIS_DIST_DIR}/${ARTEMIS_DIST_FILE_NAME}..."
    rm -rf "${ARTEMIS_DIST_DIR}"/"${ARTEMIS_DIST_FILE_NAME}"
  fi

elif [ -n "${FROM_LOCAL}" ]; then

  if [ -n "${LOCAL_DIST_PATH}" ]; then
    ARTEMIS_DIST_DIR=${LOCAL_DIST_PATH}
    echo "Using ${ARTEMIS_DIST_DIR}"
  else
     usage "You must specify the local distribution directory"
  fi

  if [ ! -d "${ARTEMIS_DIST_DIR}" ]; then
    usage "Directory ${ARTEMIS_DIST_DIR} does not exist"
  fi

  if [ -d "${ARTEMIS_DIST_DIR}/docker" ]; then
    echo "Cleaning up ${ARTEMIS_DIST_DIR}/docker"
    rm -rf "${ARTEMIS_DIST_DIR}/docker"
  fi

else

  usage

fi

if [ ! -d "${ARTEMIS_DIST_DIR}/docker" ]; then
  mkdir "${ARTEMIS_DIST_DIR}/docker"
fi

cp ./Dockerfile-* "$ARTEMIS_DIST_DIR/docker"
cp ./docker-run.sh "$ARTEMIS_DIST_DIR/docker"

next_step
