@echo off
setlocal
set DIR=%~dp0
if "%DIR%"=="" set DIR=.
cd /d "%DIR%"
set APP_HOME=%CD%
set APP_NAME=Gradle
set APP_BASE_NAME=%~n0
set DEFAULT_JVM_OPTS=
set JVM_OPTS=-Xmx2048m
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java
%JAVA_EXE% -version >NUL 2>&1
if not errorlevel 1 goto execute
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please set the JAVA_HOME environment variable or install Java.
exit /b 1
:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_EXE%" goto execute
:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JVM_OPTS% -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
