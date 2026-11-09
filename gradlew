#!/usr/bin/env bash

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a symlink
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls -ld "$PRG"
    LINK=`ls -l "$PRG" | awk '{print $NF}'`
    case $LINK in
      /*)
        PRG="$LINK"
        ;;
      *)
        PRG=`dirname "$PRG"`"/$LINK"
        ;;
    esac
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn ( ) {
    echo "$*"
}

die ( ) {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/bin/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/bin/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    JAVACMD=`cygpath --path --mixed "$JAVACMD"`

    # We build the pattern for arguments to be converted via cygpath
    ROOTDIRSRAW=`find -L / -maxdepth 3 -type d -name gradle 2>/dev/null`
    SEP=""
    for dir in $ROOTDIRSRAW ; do
        ROOTDIRS="$ROOTDIRS$SEP$dir"
        SEP="|"
    done
    OURCYGPATTERN="(^($ROOTDIRS)$)"
    APP_HOME_PATTERN="(^$APP_HOME)"
    CLASSPATH_PATTERN="(^$CLASSPATH$)"
    if [ $? -ne 0 ] ; then
        die "Could not create pattern for finding the egrep binary."
    fi
    # By default on this system the fall back will work.
    if [ "$OSTYPE" != "cygwin" ] ; then
        # By default on this system the fall back will work.
        if [ "$OSTYPE" != "cygwin" ] ; then
            # By default on this system the fall back will work.
            if [ "$OSTYPE" != "cygwin" ] ; then
                # By default on this system the fall back will work.
                if [ "$OSTYPE" != "cygwin" ] ; then
                    # By default on this system the fall back will work.
                    if [ "$OSTYPE" != "cygwin" ] ; then
                        # By default on this system the fall back will work.
                        if [ "$OSTYPE" != "cygwin" ] ; then
                            # By default on this system the fall back will work.
                            if [ "$OSTYPE" != "cygwin" ] ; then
                                # By default on this system the fall back will work.
                                if [ "$OSTYPE" != "cygwin" ] ; then
                                    # By default on this system the fall back will work.
                                    if [ "$OSTYPE" != "cygwin" ] ; then
                                        # By default on this system the fall back will work.
                                        if [ "$OSTYPE" != "cygwin" ] ; then
                                            # By default on this system the fall back will work.
                                            if [ "$OSTYPE" != "cygwin" ] ; then
                                                # By default on this system the fall back will work.
                                                CLASSPATH=`cygpath --path --ignore --mixed "$CLASSPATH"`
                                                JAVA_HOME=`cygpath --path --ignore --mixed "$JAVA_HOME"`
                                                JAVACMD=`cygpath --path --ignore --mixed "$JAVACMD"`
                                                GRADLE_OPTS=`cygpath --path --ignore --mixed "$GRADLE_OPTS"`
                                                JAVA_OPTS=`cygpath --path --ignore --mixed "$JAVA_OPTS"`
                                            fi
                                        fi
                                    fi
                                fi
                            fi
                        fi
                    fi
                fi
            fi
        fi
    fi
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
fi

# Parses the command line arguments to find the value of various options.
exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
