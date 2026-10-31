#!/usr/bin/env bash
# Temporary: prints the 26.2 HUD classes, to replace Options.hideGui.
set -u
MC=$(find ~/.gradle .gradle -name 'minecraft-merged*.jar' 2>/dev/null | grep -v sources | head -1)
[ -z "$MC" ] && MC=$(find ~/.gradle .gradle -name 'minecraft*client*.jar' 2>/dev/null | grep -v sources | head -1)
for c in net.minecraft.client.gui.Hud net.minecraft.client.renderer.state.gui.GuiRenderState; do
  echo "===== $c"; javap -cp "$MC" -public "$c" 2>&1 | head -80
done
echo "===== Gui (full)"; javap -cp "$MC" -public net.minecraft.client.gui.Gui 2>&1 | head -80
