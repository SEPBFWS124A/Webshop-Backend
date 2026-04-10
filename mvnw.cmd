@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM   https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "__MVNW_ARG0_NAME__=%~nx0")
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_LAUNCHER_MAIN_CLASS__=org.apache.maven.wrapper.MavenWrapperMain

@SETLOCAL
SET MAVEN_PROJECTBASEDIR=%~dp0
IF NOT "%MAVEN_BASEDIR%" == "" SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@SET MAVEN_OPTS_EXTRA_NOT_EMPTY=
IF NOT "%MAVEN_OPTS_EXTRA%" == "" SET MAVEN_OPTS_EXTRA_NOT_EMPTY=1

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET WRAPPER_PROPERTIES_FILE=.mvn\wrapper\maven-wrapper.properties
@SET WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\%WRAPPER_JAR%" (
    @ECHO Downloading Maven Wrapper JAR...
    FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
        IF "%%A"=="wrapperUrl" SET WRAPPER_URL=%%B
    )
    powershell -Command "& {(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%MAVEN_PROJECTBASEDIR%\%WRAPPER_JAR%')}"
)

@SET JAVA_CMD=%JAVA_HOME%\bin\java.exe
@IF NOT EXIST "%JAVA_CMD%" (
    FOR /F "tokens=*" %%I IN ('where java 2^>nul') DO @SET JAVA_CMD=%%I & GOTO :java_found
    @ECHO Error: JAVA_HOME is not set and no 'java' command found on PATH.
    EXIT /B 1
)
:java_found

@SET MAVEN_OPTS=%MAVEN_OPTS% %MAVEN_OPTS_EXTRA%
"%JAVA_CMD%" %MAVEN_OPTS% -classpath "%MAVEN_PROJECTBASEDIR%\%WRAPPER_JAR%" "%WRAPPER_LAUNCHER%" %*
