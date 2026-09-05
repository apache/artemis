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

setlocal

if NOT "%ARTEMIS_INSTANCE%"=="" goto CHECK_ARTEMIS_INSTANCE
set ARTEMIS_INSTANCE="%~dp0.."

:CHECK_ARTEMIS_INSTANCE
if exist %ARTEMIS_INSTANCE%\bin\artemis.cmd goto CHECK_JAVA

:NO_HOME
echo ARTEMIS_INSTANCE environment variable is set incorrectly. Please set ARTEMIS_INSTANCE.
goto END

:CHECK_JAVA
set _JAVACMD=%JAVACMD%

if "%JAVA_HOME%" == "" goto NO_JAVA_HOME
if not exist "%JAVA_HOME%\bin\java.exe" goto NO_JAVA_HOME
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto RUN_JAVA

:NO_JAVA_HOME
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
echo.
echo Warning: JAVA_HOME environment variable is not set.
echo.

:RUN_JAVA

rem "Load Profile Config"
set ARTEMIS_INSTANCE_ETC="${artemis.instance.etc}"

if not "%ARTEMIS_PROFILE%"=="" goto LOAD_ARTEMIS_PROFILE
set ARTEMIS_PROFILE=artemis-utility.profile.cmd
if "%1"=="run" set ARTEMIS_PROFILE=artemis.profile.cmd

:LOAD_ARTEMIS_PROFILE

IF NOT EXIST  %ARTEMIS_INSTANCE_ETC%\%ARTEMIS_PROFILE% (
       echo ********************************************************************************
       echo Error: %ARTEMIS_INSTANCE_ETC%\%ARTEMIS_PROFILE% does not exist.
       echo.
       echo This file should have been created as part of you upgrading from a previous version
       echo Please use 'artemis upgrade' or make sure you create the file.
       echo ********************************************************************************
    exit /b 1
)

call %ARTEMIS_INSTANCE_ETC%\%ARTEMIS_PROFILE% %*

if not exist %ARTEMIS_OOME_DUMP% goto NO_ARTEMIS_OOME_DUMP
rem "Backup the last OOME heap dump"
move /Y %ARTEMIS_OOME_DUMP% %ARTEMIS_OOME_DUMP%.bkp

:NO_ARTEMIS_OOME_DUMP

rem "Create full JVM Args"
set JVM_ARGS=%LOGGING_ARGS%
set JVM_ARGS=%JVM_ARGS% %JAVA_ARGS%

rem "Netty 4.2 only uses sun.misc.Unsafe on JDK 24+ when it's explicitly permitted; the NIO/MAPPED journals"
rem "rely on it, so enable it there to avoid a broker startup failure. The option is unrecognized before JDK 23,"
rem "so it's only added when recognized."
"%_JAVACMD%" --sun-misc-unsafe-memory-access=allow --version >nul 2>&1 && set JVM_ARGS=%JVM_ARGS% --sun-misc-unsafe-memory-access=allow
if not "%ARTEMIS_CLUSTER_PROPS%"=="" set JVM_ARGS=%JVM_ARGS% %ARTEMIS_CLUSTER_PROPS%
set JVM_ARGS=%JVM_ARGS% -classpath %ARTEMIS_HOME%\lib\artemis-boot.jar
set JVM_ARGS=%JVM_ARGS% -Dartemis.home=%ARTEMIS_HOME%
set JVM_ARGS=%JVM_ARGS% -Dartemis.instance=%ARTEMIS_INSTANCE%
set JVM_ARGS=%JVM_ARGS% -Ddata.dir=%ARTEMIS_DATA_DIR%
set JVM_ARGS=%JVM_ARGS% -Dartemis.instance.etc=%ARTEMIS_INSTANCE_ETC%
set JVM_ARGS=%JVM_ARGS% -Djava.security.auth.login.config=%ARTEMIS_INSTANCE_ETC%\login.config

if not "%DEBUG_ARGS%"=="" set JVM_ARGS=%JVM_ARGS% %DEBUG_ARGS%
if not "%$JAVA_ARGS_APPEND%"=="" set JVM_ARGS=%JVM_ARGS% %$JAVA_ARGS_APPEND%

"%_JAVACMD%" %JVM_ARGS% org.apache.activemq.artemis.boot.Artemis %*

:END
endlocal
GOTO :EOF

:EOF
