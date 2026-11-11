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

if NOT "%ARTEMIS_HOME%"=="" goto CHECK_ARTEMIS_HOME
set ARTEMIS_HOME="%~dp0.."

:CHECK_ARTEMIS_HOME
if exist %ARTEMIS_HOME%\bin\artemis.cmd goto CHECK_JAVA

:NO_HOME
echo ARTEMIS_HOME environment variable is set incorrectly. Please set ARTEMIS_HOME.
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

rem "Set Defaults."
if "%JAVA_ARGS%" == "" set JAVA_ARGS=-Xms512M -Xmx1024M

rem "Create full JVM Args"
set JVM_ARGS=%JAVA_ARGS%
if not "%ARTEMIS_CLUSTER_PROPS%"=="" set JVM_ARGS=%JVM_ARGS% %ARTEMIS_CLUSTER_PROPS%
set JVM_ARGS=%JVM_ARGS% -classpath %ARTEMIS_HOME%\lib\artemis-boot.jar
set JVM_ARGS=%JVM_ARGS% -Dartemis.home=%ARTEMIS_HOME%
if not "%DEBUG_ARGS%"=="" set JVM_ARGS=%JVM_ARGS% %DEBUG_ARGS%
if not "%JAVA_ARGS_APPEND%"=="" set JVM_ARGS=%JVM_ARGS% %JAVA_ARGS_APPEND%

"%_JAVACMD%" %JVM_ARGS% org.apache.activemq.artemis.boot.Artemis %*

:END
endlocal
GOTO :EOF

:EOF
