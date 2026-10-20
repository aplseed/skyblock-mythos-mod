#!/usr/bin/env bash
# Temporary: prints the client game-test API this build is written against.
set -u
MC=$(find ~/.gradle .gradle -name 'minecraft-merged*.jar' 2>/dev/null | grep -v sources | head -1)
GT=$(find ~/.gradle -name 'fabric-client-gametest-api-v1*.jar' 2>/dev/null | grep -v sources | head -1)
FAPI=$(find ~/.gradle -name 'fabric-*.jar' -path '*net.fabricmc.fabric-api*' 2>/dev/null | grep -v sources | sort -u | tr '\n' ':')
echo "GT jar: $GT"
unzip -l "$GT" | awk '{print $4}' | grep '\.class$' | grep -v impl
unzip -p "$GT" fabric.mod.json
CP="$MC:$FAPI"
for c in $(unzip -l "$GT" | awk '{print $4}' | grep 'api/.*\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do
  echo "===== $c"; javap -cp "$CP" -public "$c" 2>&1 | head -80
done
for c in net.minecraft.world.inventory.ChestMenu net.minecraft.client.gui.screens.inventory.ContainerScreen net.minecraft.world.SimpleContainer net.minecraft.client.gui.components.toasts.SystemToast\$SystemToastId; do
  echo "===== $c"; javap -cp "$CP" -public "$c" 2>&1 | head -40
done
echo "===== loom test DSL"
find ~/.gradle/caches -path '*fabric-loom*' -name '*.jar' 2>/dev/null | head -3
J=$(find ~/.gradle/caches -path '*fabric-loom*' -name 'fabric-loom-*.jar' 2>/dev/null | grep -v sources | head -1)
unzip -l "$J" | awk '{print $4}' | grep -i 'gametest\|FabricApiTesting\|FabricApiExtension' | head -20
for c in $(unzip -l "$J" | awk '{print $4}' | grep -iE 'api/fabricapi/.*\.class$|FabricApiTesting|FabricApiExtension' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' | head -6); do
  echo "===== $c"; javap -cp "$J" -public "$c" 2>&1 | head -60
done
