#!/usr/bin/env bash
args=${@:3}

export MAVEN_OPTS=${MAVEN_OPTS:-"-Xmx15g"}

if (( $# > 0 )); then
    if [[ -z "${logfile}" ]]; then
        echo "logfile not set, will use the default log file"
        mvn exec:java -pl $1 -Dexec.mainClass="$2" -Dexec.args="$args"
    else
        echo "logfile provided at: "${logfile}
        mvn exec:java -pl $1 -Dexec.mainClass="$2" -Dexec.args="$args" -Dlogback.configurationFile=${logfile}
    fi
fi
