./gradlew build
cp build/libs/*.jar ~/paper12110_geyser/plugins/
mcrcon -H 127.0.0.1 -P 25575 -p $(cat managementpass.txt) "stop"
