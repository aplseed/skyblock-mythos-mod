#!/usr/bin/env bash
# Temporary: prints the 26.2 API behind the two broken calls (hideGui, getToastManager).
set -u
MC=$(find ~/.gradle .gradle -name 'minecraft-merged*.jar' 2>/dev/null | grep -v sources | head -1)
[ -z "$MC" ] && MC=$(find ~/.gradle .gradle -name 'minecraft*client*.jar' 2>/dev/null | grep -v sources | head -1)
echo "MC jar: $MC"
echo "===== classes named *Toast*"
unzip -l "$MC" | awk '{print $4}' | grep -i 'toast' | grep -v '\$'
for c in net.minecraft.client.Options net.minecraft.client.Minecraft net.minecraft.client.gui.Gui \
  net.minecraft.client.gui.components.toasts.SystemToast net.minecraft.client.gui.components.toasts.ToastManager; do
  echo "===== $c (filtered)"
  javap -cp "$MC" -public "$c" 2>&1 | grep -iE 'class |hide|gui|toast|hud|error' | head -60
done
echo "===== who references ToastManager"
for c in $(unzip -l "$MC" | awk '{print $4}' | grep '^net/minecraft/client/.*\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do
  javap -cp "$MC" -public "$c" 2>/dev/null | grep -q 'ToastManager' && { echo "-- $c"; javap -cp "$MC" -public "$c" | grep 'ToastManager'; }
done | head -40
echo "===== who has a hideGui-like member"
for c in $(unzip -l "$MC" | awk '{print $4}' | grep '^net/minecraft/client/.*\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do
  javap -cp "$MC" -public "$c" 2>/dev/null | grep -iE 'hide(Gui|Hud)|(gui|hud)Hidden|isGuiHidden|renderGui|showGui|showHud' | sed "s#^#$c: #"
done | head -40
