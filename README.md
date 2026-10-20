# Mythos

Client-side Fabric mod for Hypixel SkyBlock. It's built for **Minecraft 26.1.2**, **Fabric Loader 0.19.5** and Fabric API 0.155.3+26.1.2.

Mythos is the in-game half of [skyblock-mythos](https://github.com/aplseed/skyblock-mythos). The server does the work: fusion optimisation, absorb costs, forge ranking, backtested market signals, mayor effects and tax-aware P&L. The mod reads what only the game client can see, which is the open menu, your inventory and chat. It shows the server's answer next to the menu where you're making the decision.

It doesn't repeat features covered by Skyblocker, SkyHanni, Firmament, BtrBz or MarketGuard. That means no price tooltips, flipping, outbid alerts or timers. Everything Mythos does is display-only: no clicks or actions are automated.

## Features

| | Where | What you see |
| --- | --- | --- |
| **Fusion planner** | Fusion Box, Hunting Box | A side panel built from the shards you hold. It shows the server's optimal fusion plan: which fusions to run and how many times, the profit, which shards to sell raw, and how much fusing adds over selling everything raw. Shards the plan fuses are tinted green; shards it sells raw are tinted gold. |
| **Absorb sync** | Attribute Menu, Hunting Box, Fusion Box | Your absorbed count for each shard is read and stored on the server, so the web absorb tracker stays current. In the Hunting Box and Fusion Box, only maxed shards can be read, and they're stored at their cap. In the Attribute Menu, the panel also shows the cost to max everything, the cheapest shards to finish, and for the hovered shard its cost per unit and whether buying or fusing is cheaper. Click a shard in the list (or "Finish" for the hovered one) to run `/bz <shard> Shard` and copy the amount still needed to the clipboard. |
| **Bazaar intel** | Any bazaar menu | For the product page, or the item under the mouse: <br>• active signals (Undervalued, Uptrend, Supply flood…) with their backtested hit rate and average return compared with buying anything at random <br>• your open position and its P&L after tax <br>• how the current and next mayor moved the item in past terms |
| **Forge advisor** | The Forge and its recipe menus | Recipes ranked by profit per slot-hour, using your slot count, Quick Forge level and Cole. Rank badges appear on recipe items, each recipe category lists its best recipes, running processes show their time left, and there's a "start this" list when a slot is free. Hovering a recipe shows its profit, cost, time, HotM requirement, which parts to forge yourself and what to buy. |
| **Election panel** | Election booth | For every candidate, the items that rose or fell in all their past terms (measured by the server). Where nothing has been measured yet, it shows what their perks are expected to do. |
| **Sell warnings** | Chat + toast | When the engine flags something you hold, you're told once. The warning is acknowledged for the mod only, so the web toast still shows. |
| **Shard sessions** *(experimental)* | HUD | Shards caught (read from chat), their net value, coins per hour, and your main shard's catch rate compared with the rate saved for it on the Shard Farm page. |

## Setup

1. Run the [skyblock-mythos](https://github.com/aplseed/skyblock-mythos) server. The mod connects to `http://127.0.0.1:5658` by default; use `/myth url <url>` for a server on another machine.
2. Put `mythos-<version>.jar` in `.minecraft/mods` alongside Fabric API.
3. In game, run `/myth login <user> <password>` once, using an account from the server's account page. The mod keeps only the session token it gets back, never your password. Sessions last 30 days from their last use; changing your password ends the session, and you then log in again.

The mod does nothing on servers other than Hypixel unless you set `onlyOnHypixel` to false.

## Commands

| Command | |
| --- | --- |
| `/myth` | Status: backend, login, features, settings |
| `/myth login <user> <password>` / `/myth logout` | Backend account |
| `/myth url <url>` | Backend address |
| `/myth toggle <fusion\|absorb\|bazaar\|forge\|election\|warnings\|shards\|panels>` | Turn a feature on or off |
| `/myth forge <slots> <quickForgeLevel> <cole true\|false>` | Your forge unlocks, used for the ranking |
| `/myth fusion <sell_order\|instant_sell> <pureReptileLevel>` | How fusion plans value output |
| `/myth session` / `/myth session stop` | Current shard session / end it |
| `/myth hud <x> <y>` | Shard session HUD position |

## Keys

Both keys can be rebound in Options → Controls → Mythos.

- **F8 — dump menu.** With any menu open, press F8 to save its title and every item's name, id and lore to `config/mythos/dumps/`, and copy the same text to the clipboard. Chat can't be opened over a menu, so this is how you report a menu the mod reads wrongly. Paste the dump into an issue.
- **Toggle menu panels.** Unbound by default.

## Config

Settings are stored in `.minecraft/config/mythos.json`. You can edit them there or with the commands above.

## Building

Builds need JDK 25.

```
./gradlew build
```

The jar is written to `build/libs/`. CI builds every push and attaches the jar to the run as the `mythos-jar` artifact.

## Server endpoints used

- `POST /api/auth/login`, `POST /api/auth/logout`
- `GET /api/mod/shards`, `GET /api/mod/bazaar-names`, `GET /api/mod/item`, `GET /api/mod/election`, `POST /api/mod/absorb`
- `POST /api/shards/optimize-inventory`, `GET /api/shards/absorb`, `GET /api/shards/farms`
- `GET /api/forge?compact=1`
- `GET /api/status`, `POST /api/alerts/ack`

## Known limitations

- **Menu formats come from real dumps, and Hypixel can change them.** What the readers rely on:
  - Shards are identified by the id in their lore: `(ID C28)` in the Hunting Box and Fusion Box, `Source: Grove Shard (C1)` in the Attribute Menu.
  - Shard counts come from "Owned: n Shards" (Hunting Box) and "Hunting Box: n" (Fusion Box).
  - The game never shows an absorbed count directly, so it's worked out from the lore: "Attribute Maxed!", "(Max level reached)" or "Attribute Level: 10 (MAX!)" means the cap; otherwise it's the cap minus "Syphon n shards to max!" (or "n more to max" while locked). This relies on the server's caps per rarity (Common 96, Uncommon 64, Rare 48, …) matching the game's.
  - In The Forge, a free slot is "Slot #n" with "Click to select a process!", and a busy one is the product with "Currently making:" and "Time Remaining:". Recipes are ranked by their SkyBlock id, and the panel only shows in a recipe category ("Tools", "Pets", "Other"…) when the menu lists recipes ("Items Required", "Duration:"), since other menus share those titles.

  If a panel shows nothing, or reads numbers wrongly, press F8 in that menu and send the dump.
- **The Attribute Menu syncs one page at a time.** Each of its pages is synced when you open it.
- **The Hunting Box planner only sees the page that's open.** It counts the shards shown on the current page plus your inventory.
- **Shard sessions count chat messages that name a shard.** Catch messages worded in ways the patterns don't match aren't counted.
