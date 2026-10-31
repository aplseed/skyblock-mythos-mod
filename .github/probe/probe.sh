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
# One javap over every client class; keep the class header lines so matches can be attributed.
unzip -l "$MC" | awk '{print $4}' | grep '^net/minecraft/client/.*\.class$' | grep -v '\$' \
  | sed 's/\.class$//; s#/#.#g' > /tmp/classes.txt
xargs -a /tmp/classes.txt javap -cp "$MC" -public 2>/dev/null > /tmp/all.txt
echo "===== members mentioning ToastManager"
awk '/^(public |final |abstract )*(class|interface|enum|record) /{cls=$0} /ToastManager/{print cls " :: " $0}' /tmp/all.txt | head -40
echo "===== hideGui-like members"
awk '/^(public |final |abstract )*(class|interface|enum|record) /{cls=$0} tolower($0) ~ /hide(gui|hud)|(gui|hud)hidden|showgui|showhud/{print cls " :: " $0}' /tmp/all.txt | head -40
