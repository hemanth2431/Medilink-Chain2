@echo off
REM Build script for MediLinkChain - uses Android Studio's bundled JDK
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JDK: %JAVA_HOME%
call gradlew.bat assembleDebug %*
