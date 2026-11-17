# Mythos

Client-side Fabric mod for Hypixel SkyBlock, for **Minecraft 26.2** with Fabric Loader 0.19.5 or newer and Fabric API. The same source also builds a jar for **26.3** (see [Building](#building)).

Mythos is the in-game half of [skyblock-mythos](https://github.com/aplseed/skyblock-mythos). The server does the work: fusion optimisation, absorb costs, forge ranking, backtested market signals, mayor effects and tax-aware P&L. The mod reads what only the game client can see, which is the open menu, your inventory and chat. It shows the server's answer next to the menu where you're making the decision.

It doesn't repeat features covered by Skyblocker, SkyHanni, Firmament, BtrBz or MarketGuard. That means no price tooltips, flipping, outbid alerts or timers. Everything Mythos does is display-only: no clicks or actions are automated.

The one part that needs no server is the **accessory calculator**: it reads your Accessory Bag and prices what's missing from public data.

## Features

| | Where | What you see |
| --- | --- | --- |
| **Fusion planner** | Fusion Box, Hunting Box | A side panel built from the shards you hold. It shows the server's optimal fusion plan: which fusions to run and how many times, the profit, which shards to sell raw, and how much fusing adds over selling everything raw. Shards the plan fuses are tinted green; shards it sells raw are tinted gold. |
| **Absorb sync** | Attribute Menu, Hunting Box, Fusion Box | Your absorbed count for each shard is read and stored on the server, so the web absorb tracker stays current. In the Hunting Box and Fusion Box, only maxed shards can be read, and they're stored at their cap. In the Attribute Menu, the panel also shows the cost to max everything, the cheapest shards to finish, and for the hovered shard its cost per unit and whether buying or fusing is cheaper. Click a shard in the list (or "Finish" for the hovered one) to run `/bz <shard> Shard` and copy the amount still needed to the clipboard. |
| **Bazaar intel** | Any bazaar menu | For the product page, or the item under the mouse: <br>• active signals (Undervalued, Uptrend, Supply flood…) with their backtested hit rate and average return compared with buying anything at random <br>• your open position and its P&L after tax <br>• how the current and next mayor moved the item in past terms |
| **Forge advisor** | The Forge and its recipe menus | Recipes ranked by profit per slot-hour, using your slot count, Quick Forge level and Cole. Rank badges appear on recipe items, each recipe category lists its best recipes, running processes show their time left, and there's a "start this" list when a slot is free. Hovering a recipe shows its profit, cost, time, HotM requirement, which parts to forge yourself and what to buy. |
| **Election panel** | Election booth | For every candidate, the items that rose or fell in all their past terms (measured by the server). Where nothing has been measured yet, it shows what their perks are expected to do. |
| **Sell warnings** | Chat + toast | When the engine flags something you hold, you're told once. The warning is acknowledged for the mod only, so the web toast still shows. |
| **Accessory calculator** | Accessory Bag, `/myth acc` | Scans the bag, finds spare accessories, lists what's missing and plans a budget. See [below](#accessory-calculator). |
| **Shard sessions** *(experimental)* | HUD | Shards caught (read from chat), their net value, coins per hour, and your main shard's catch rate compared with the rate saved for it on the Shard Farm page. |

Panels sit beside the menu on whichever side has room, never over it, and scroll with the mouse wheel when they're taller than the screen.

## The Mythos window

`/myth` (or the "Open the Mythos window" key) opens one window for everything else:

- **Overview** - server address and login (the password field is masked, and only the session token is stored), plus a switch for every feature.
- **Accessories** - the calculator: Summary, Missing, Dupes, Budget and Unlocks.
- **Settings** - forge slots, Quick Forge and Cole, how fusion plans value output, the shard HUD position.

## Accessory calculator

Built for the Accessory Bag as it is since SkyBlock 0.27.3, where every accessory line has its own slot. Open the bag once, every page of it (any sort order works). Mythos records the accessory in each line (id, rarity, whether it's recombobulated) and which lines are empty, and keeps the scan in `config/mythos/accessories.json`, so the calculator works afterwards without the bag open. The bag itself gets a panel with your Accessory Power (formerly Magical Power), free slots, spare accessories (tinted red in your inventory) and the cheapest power you could buy next.

Free slots come from the game's own count: a "Slots: used/total" line in the bag, or the Accessory Bag widget in the tab list (`/tab` to turn it on). Without either, set them in Unlocks or with `/myth acc slots <n>`.

- **Spares** - the bag holds one accessory per line, and only that one counts. Accessories in your inventory that the bag already has (or has a better tier of) are listed, so you can sell them.
- **Missing** - every line you don't have, or have a lower tier of, cheapest coins per Accessory Power first. Click one to run `/ahs <name>`, or `/recipe <name>` if the cheapest way is to craft it. Steps of a budget plan and the bag panel's list work the same way.
- **Collections** - open each Collections category once (Farming, Mining...) and Mythos records your tiers. A craft is only recommended when the collection that unlocks its recipe is high enough; until a collection has been seen, its recipes aren't used and the accessory is priced as a purchase. The tiers can also be set in Unlocks or with `/myth acc collection <tier> <name>`.
- **Budget** - `/myth budget 50m` (or the Budget tab) works out the most Accessory Power the coins can buy. It weighs, together:
  - new accessories at their lowest buy-it-now auction (no BIN listed: not buyable), or crafted when that's cheaper;
  - upgrading what you have: the next tiers of a line, crafted *from the tier you own* where the recipe allows, and the cost of the rest of the recipe;
  - a Recombobulator 3000 on an accessory you own or one it tells you to buy (never on ones that can't take it or already have one);
  - bag space: a new line needs a free slot; upgrading a line keeps its slot. Slots come from the free ones, then from Jacobus' bag upgrades at their 0.27.3 price (100k, 250k, 500k, 1M, then 1.5M rising by 500k to 9.5M, then 10M each; +2 slots per upgrade; 875M for all 99) - bought only when the power is worth it;
  - what you can use: accessories behind a slayer level, Heart of the Mountain tier, trophy fish or Melody's Harp you don't have are left out.

  The result is exact, not a greedy guess: it's a knapsack over coins and slots solved over every combination, and it takes milliseconds.
- **Unlocks** - your slayer levels and the other requirements, how many bag upgrades you've bought from Jacobus, free slots, and a few switches. Mythos can't read these from the game, so set them once. A requirement left at "not set" counts as locked; with Jacobus at "unknown", budgets don't buy slots.

Accessory Power per rarity: Common 3, Uncommon 5, Rare 8, Epic 12, Legendary 16, Mythic 22, Divine 28. Special and Very Special accessories give none since 0.27.3. The Hegemony Artifact counts twice. The Rift Prism is an ordinary accessory in the bag now (it can no longer be consumed for +11), and can be recombobulated.

Data comes from Hypixel's public item list and bazaar (`api.hypixel.net`), upgrade lines and recipes from the NotEnoughUpdates repo (`raw.githubusercontent.com`), and and accessory prices from Hypixel's own list of active auctions. An accessory is only ever planned as a purchase when a buy-it-now auction for it is open right now (not ended, not already bought); one without is either crafted or left out. Third-party lowest-BIN lists (`moulberry.codes`, then `hysky.de`) are used for crafting ingredients only, because they keep the last price of items nobody is selling any more. Prices refresh every 5 minutes and the auction list (about 40 requests of a megabyte each) every 10, both only while the calculator or the bag is in use; the item list is cached for a day and recipes for a week in `config/mythos/cache`.

## Setup

1. Put the `mythos-<version>+mc<minecraft version>.jar` that matches your game in `.minecraft/mods` alongside Fabric API. The accessory calculator works from here on.
2. For everything else, run the [skyblock-mythos](https://github.com/aplseed/skyblock-mythos) server. The mod connects to `http://127.0.0.1:5658` by default; change the address in `/myth` (or `/myth url <url>`) for a server on another machine.
3. In game, open `/myth` and log in once with an account from the server's account page (`/myth login <user> <password>` does the same). The mod keeps only the session token it gets back, never your password. Sessions last 30 days from their last use; changing your password ends the session, and you then log in again.

The mod does nothing on servers other than Hypixel unless you set `onlyOnHypixel` to false.

## Commands

| Command | |
| --- | --- |
| `/myth` | Opens the Mythos window |
| `/myth status` | Server, login, features and settings, in chat |
| `/myth login <user> <password>` / `/myth logout` | Server account |
| `/myth url <url>` | Server address |
| `/myth toggle <panels\|accessories\|fusion\|absorb\|bazaar\|forge\|election\|warnings\|shards>` | Turn a feature on or off |
| `/myth forge <slots> <quickForgeLevel> <cole true\|false>` | Your forge unlocks, used for the ranking |
| `/myth fusion <sell_order\|instant_sell> <pureReptileLevel>` | How fusion plans value output |
| `/myth session` / `/myth session stop` | Current shard session / end it |
| `/myth hud <x> <y>` | Shard session HUD position |
| `/myth budget <coins>` | Best way to spend that much on Accessory Power (`50m`, `1.2b`, `750k`) |
| `/myth acc` | Opens the accessory calculator |
| `/myth acc dupes` / `/myth acc missing` | Spare accessories / cheapest missing accessories, in chat |
| `/myth acc unlock <requirement> <level>` | Your level for a requirement: `slayer_wolf 7`, `hotm 5`, `trophy_lava 3` (0 none - 4 diamond), `melody 1` |
| `/myth acc collection <tier> <name>` | A collection's tier, if the menu wasn't read: `collection 9 Feather` |
| `/myth acc jacobus <upgradesBought>` | Bag upgrades bought from Jacobus (0-99, -1 unknown) |
| `/myth acc slots <free\|auto>` | Set the free bag slots, if the game's count isn't read |
| `/myth acc refresh` / `/myth acc reset` | Reload prices and the item list / forget the scanned bag |

## Keys

All three can be rebound in Options → Controls → Mythos.

- **Open the Mythos window.** Unbound by default; `/myth` does the same.

- **F8 — dump menu.** With any menu open, press F8 to save its title and every item's name, id and lore to `config/mythos/dumps/`, and copy the same text to the clipboard. Chat can't be opened over a menu, so this is how you report a menu the mod reads wrongly. Paste the dump into an issue.
- **Toggle menu panels.** Unbound by default.

## Config

Settings are stored in `.minecraft/config/mythos.json`. You can edit them there or with the commands above.

## Building

Builds need JDK 25.

```
./gradlew build
```

The jar is written to `build/libs/` for the Minecraft version in `gradle.properties` (26.2). For 26.3, name it and its Fabric API:

```
./gradlew build -Pminecraft_version=26.3 -Pfabric_api_version=0.166.2+26.3
```

A jar only loads on the Minecraft version it was built for (26.3 renumbered its key codes).

`build` also runs the unit tests of the accessory calculator (`src/test`). `./gradlew runClientGameTest` boots the real client against recorded server responses (`.github/fake-backend`, start `server.py` first) and checks what every panel and window tab draws; with `MYTHOS_LIVE=1` set it also loads the real accessory data and prices. CI does both for each Minecraft version and attaches the jars as `mythos-jar-<version>`.

## Server endpoints used

- `POST /api/auth/login`, `POST /api/auth/logout`
- `GET /api/mod/shards`, `GET /api/mod/bazaar-names`, `GET /api/mod/item`, `GET /api/mod/election`, `POST /api/mod/absorb`
- `POST /api/shards/optimize-inventory`, `GET /api/shards/absorb`, `GET /api/shards/farms`
- `GET /api/forge?compact=1`
- `GET /api/status`, `POST /api/alerts/ack`

The accessory calculator calls none of these.

## Known limitations

- **Menu formats come from real dumps, and Hypixel can change them.** What the readers rely on:
  - Shards are identified by the id in their lore: `(ID C28)` in the Hunting Box and Fusion Box, `Source: Grove Shard (C1)` in the Attribute Menu.
  - Shard counts come from "Owned: n Shards" (Hunting Box) and "Hunting Box: n" (Fusion Box).
  - The game never shows an absorbed count directly, so it's worked out from the lore: "Attribute Maxed!", "(Max level reached)" or "Attribute Level: 10 (MAX!)" means the cap; otherwise it's the cap minus "Syphon n shards to max!" (or "n more to max" while locked). This relies on the server's caps per rarity (Common 96, Uncommon 64, Rare 48, …) matching the game's.
  - In The Forge, a free slot is "Slot #n" with "Click to select a process!", and a busy one is the product with "Currently making:" and "Time Remaining:". Recipes are ranked by their SkyBlock id, and the panel only shows in a recipe category ("Tools", "Pets", "Other"…) when the menu lists recipes ("Items Required", "Duration:"), since other menus share those titles.

  If a panel shows nothing, or reads numbers wrongly, press F8 in that menu and send the dump.
- **The Attribute Menu syncs one page at a time.** Each of its pages is synced when you open it.
- **The 0.27.3 Accessory Bag reader hasn't been checked against a real menu dump yet** (the update released the day this was written). It expects the title "Accessory Bag" or "Accessory Bag (2/3)", sorting and navigation in the bottom row, a filled slot holding the accessory itself (SkyBlock id, rarity line ending in ACCESSORY), and an empty one either without an id or saying in its lore that the accessory is missing. If the panel counts accessories you don't have, or the free slots are off, press F8 in the bag and send the dump.
- **Accessory Power can differ slightly from the game's.** The Abicase's bonus from contacts isn't counted, Dungeon accessories' double power inside Dungeons isn't either, and an accessory in the bag that you don't meet the requirement for is counted as if it worked. When the game shows its own total (tab widget or bag), the panels show it beside Mythos' number.
- **The Collections reader hasn't been checked against a real menu dump either.** It expects category menus titled "Farming Collections" and so on, with each collection as an item named with its tier ("Feather IX"). If your tiers don't appear under Unlocks after opening them, set them by hand and send an F8 dump.
- **Recipe requirements other than slayer levels and collections** (shown as "needs ..." in the step) can't be checked. Turn off "Craft when it's cheaper" in Unlocks to plan with auction prices only. Crafting is assumed to drop a recombobulator on the ingredient.
- **Soulbound and unsold accessories** (campfire badges, the Jacobus Register...) have no price, so they're listed as missing but never planned.
- **One profile.** The scan isn't tied to a SkyBlock profile; after switching, use "Forget the scan" in Unlocks.
- **The Hunting Box planner only sees the page that's open.** It counts the shards shown on the current page plus your inventory.
- **Shard sessions count chat messages that name a shard.** Catch messages worded in ways the patterns don't match aren't counted.
