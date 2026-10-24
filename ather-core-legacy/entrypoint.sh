#!/bin/bash
export ENV_MAX_HEAP_SIZE=$2
export ENV_MIN_HEAP_SIZE=$3

# Check if lib directory exists and has dependencies
if [ -d "lib" ] && [ "$(ls -A lib)" ]; then
    # Use external lib directory if available
    java -Xmx${ENV_MAX_HEAP_SIZE:-"512m"} -Xms${ENV_MIN_HEAP_SIZE:-"64m"}  -Dconfig=etc/config.properties \
         --add-opens=java.base/java.nio=ALL-UNNAMED \
         -cp "$1:lib/*" io.ather.server.atherServer
else
    # Use fat JAR directly (no external dependencies needed)
    java -Xmx${ENV_MAX_HEAP_SIZE:-"512m"} -Xms${ENV_MIN_HEAP_SIZE:-"64m"}  -Dconfig=etc/config.properties \
         --add-opens=java.base/java.nio=ALL-UNNAMED \
         -jar "$1"
fi
