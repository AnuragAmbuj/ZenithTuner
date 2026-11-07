#!/bin/sh

# ZenithTuner Gradle Wrapper Script

# Resolve directory of this script
PRG="$0"
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Locate gradle if wrapper jar is absent
GRADLE_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$GRADLE_JAR" ]; then
    # Try using system / cached gradle
    CACHED_GRADLE=$(find "$HOME/.gradle/wrapper/dists" -name "gradle" -type f 2>/dev/null | head -n 1)
    if [ -n "$CACHED_GRADLE" ] && [ -x "$CACHED_GRADLE" ]; then
        exec "$CACHED_GRADLE" "$@"
    elif command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    fi
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$GRADLE_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
