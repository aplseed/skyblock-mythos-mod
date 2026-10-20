#!/usr/bin/env bash
# Prints the 26.1.2 / Fabric API signatures the mod is written against.
# Temporary: removed once the code compiles.
set -u
MC=$(find ~/.gradle .gradle -name '*.jar' 2>/dev/null | while read j; do
  unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/client/Minecraft.class' && echo "$j"; done | grep -v sources | head -1)
echo "MC jar: $MC"
FAPI=$(find ~/.gradle -name 'fabric-*.jar' -path '*net.fabricmc.fabric-api*' 2>/dev/null | grep -v sources | sort -u)
CP="$MC:$(echo $FAPI | tr ' ' ':')"

echo "===== class names"
unzip -l "$MC" | awk '{print $4}' | grep -E '^net/minecraft/(client/gui/(GuiGraphics|Font|screens/inventory/(AbstractContainerScreen|ContainerScreen)|components/toasts/(SystemToast|ToastManager|Toast)|components/(PlayerTabOverlay))|client/(Minecraft|KeyMapping)|client/multiplayer/(ClientPacketListener|ServerData|PlayerInfo)|world/inventory/(Slot|ChestMenu|AbstractContainerMenu)|world/item/(ItemStack|component/ItemLore|component/CustomData)|core/component/DataComponents|world/scores/(Scoreboard|Objective|DisplaySlot|PlayerTeam|PlayerScoreEntry)|network/chat/(Component|Style|ClickEvent|HoverEvent)|resources/Identifier|util/ARGB|client/input/(KeyEvent|MouseButtonEvent))[^/]*\.class$'
for p in client/gui/GuiGraphics client/gui/GuiGraphicsExtractor; do :; done

echo "===== fabric classes"
for j in $FAPI; do unzip -l "$j" | awk '{print $4}' | grep -E '^net/fabricmc/fabric/api/client/(screen|message|command|event/lifecycle|keymapping|keybinding|rendering/v1|item|networking/v1/ClientPlayConnection)[^$]*\.class$'; done | sort -u

jp() { echo "===== $1"; javap -cp "$CP" -public "$1" 2>&1 | head -${2:-200}; }
for c in \
  net.minecraft.client.Minecraft:400 \
  net.minecraft.client.gui.GuiGraphics \
  net.minecraft.client.gui.GuiGraphicsExtractor \
  net.minecraft.client.gui.Font \
  net.minecraft.client.gui.screens.Screen \
  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen \
  net.minecraft.client.KeyMapping \
  net.minecraft.client.KeyMapping\$Category \
  net.minecraft.client.input.KeyEvent \
  net.minecraft.client.gui.components.toasts.SystemToast \
  net.minecraft.client.gui.components.toasts.ToastManager \
  net.minecraft.client.multiplayer.ServerData \
  net.minecraft.client.multiplayer.ClientPacketListener \
  net.minecraft.world.inventory.Slot \
  net.minecraft.world.inventory.AbstractContainerMenu \
  net.minecraft.world.item.ItemStack:300 \
  net.minecraft.world.item.component.ItemLore \
  net.minecraft.world.item.component.CustomData \
  net.minecraft.world.scores.Scoreboard \
  net.minecraft.world.scores.Objective \
  net.minecraft.world.scores.DisplaySlot \
  net.minecraft.world.scores.PlayerTeam \
  net.minecraft.world.scores.PlayerScoreEntry \
  net.minecraft.network.chat.Component \
  net.minecraft.network.chat.ClickEvent \
  net.minecraft.network.chat.HoverEvent \
  net.minecraft.resources.Identifier \
  net.minecraft.client.player.LocalPlayer:120 \
  net.minecraft.world.entity.player.Inventory \
  net.minecraft.client.gui.components.PlayerTabOverlay \
  net.minecraft.client.DeltaTracker \
  net.fabricmc.fabric.api.client.screen.v1.ScreenEvents \
  net.fabricmc.fabric.api.client.screen.v1.ScreenEvents\$AfterExtract \
  net.fabricmc.fabric.api.client.screen.v1.ScreenEvents\$AfterRender \
  net.fabricmc.fabric.api.client.screen.v1.ScreenEvents\$AfterInit \
  net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents \
  net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents\$AfterKeyPress \
  net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents \
  net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents\$AllowMouseScroll \
  net.fabricmc.fabric.api.client.screen.v1.Screens \
  net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents \
  net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents\$Game \
  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback \
  net.fabricmc.fabric.api.client.command.v2.ClientCommandManager \
  net.fabricmc.fabric.api.client.command.v2.ClientCommands \
  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource \
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents \
  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper \
  net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper \
  net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry \
  net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement \
  net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements \
  net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback \
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents \
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents\$Join \
  ; do jp "${c%%:*}" "$( [[ $c == *:* ]] && echo ${c##*:} || echo 200)"; done
