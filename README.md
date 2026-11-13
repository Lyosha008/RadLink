# RadLink Mod Documentation

RadLink is a Minecraft Forge mod that lets you create custom items, events, blocks, armor, effects, and fully customize the game's Main Menu using flexible JSON configurations and a built-in script engine.

# ATTENTION!
* Not all files here are updated all the time, so taking world files, for example, Changi or player files, is not the best idea.

---

## Table of Contents

1. [Folder Structure and File Locations](#1-folder-structure-and-file-locations)
   - [Custom crafting recipes](#custom-crafting-recipes)
   - [Custom commands](#custom-commands)
   - [Custom items and item models](#custom-items-and-item-models)
   - [Custom blocks and block models](#custom-blocks-and-block-models)
   - [Custom potion effects](#custom-potion-effects)
   - [Names, textures, and reloading](#names-textures-and-reloading)
2. [Mod Configuration (`radlink-client.toml`)](#2-mod-configuration-radlink-clienttoml)
3. [Main Menu Customization (`menu_background.json`)](#3-main-menu-customization-menu_backgroundjson)
4. [Script System Basics](#4-script-system-basics)
5. [Conditions (`if` / `If`)](#5-conditions-if--if)
   - [`query` vs `calc` — What's the Difference?](#query-vs-calc--whats-the-difference)
   - [Complete Condition Reference](#complete-condition-reference)
6. [Actions (`then` / `Then`)](#6-actions-then--then)
7. [Variables and Placeholders](#7-variables-and-placeholders)
8. [Branching Logic (`case`)](#8-branching-logic-case)

---

## 1. Folder Structure and File Locations

Script and content files are stored with the world/server data. For multiplayer, RadLink transfers the server's content to the client during the Forge connection handshake, before registry synchronization. The main-menu background is read from the local cache on the next game launch.

### A) Single-Player Worlds
```
run/saves/<World_Name>/radlink/
```

### B) Automatically synchronized multiplayer content
```
<Minecraft game folder>/radlink/servers/<sanitized server address>/pack-<content hash>-<transfer id>/
```
Every character except English letters, digits, `_`, and `-` in the configured address is replaced with `_`. The port is part of the address and therefore part of the folder name. For example, `RadLink.aternos.me:29550` becomes:
```
<Minecraft game folder>/radlink/servers/RadLink_aternos_me_29550/
```
Clients do not need to copy these files manually. Only RadLink definitions, `pack.mcmeta`, and files under `assets/` or `data/` are transferred; unrelated files and `variables.json` are not sent.

### Available files inside `radlink/`

| File | Purpose |
|------|---------|
| `assets/` | Custom resources and textures |
| `data/` | Generation data and data packs |
| `armor_item.json` | Settings and scripts for custom armor items |
| `blocks.json` | Behavior and event scripts for custom blocks |
| `effects.json` | Registration and parameters for custom potion effects |
| `global.json` | Global events and world tickers |
| `items.json` | Item scripts and definitions (including interaction triggers) |
| `menu_background.json` | Customization of the main menu and buttons |
| `variables.json` | Dynamic world variable storage |
| `pack.mcmeta` | Metadata for datapack / resource pack |

### Custom crafting recipes

Recipes use Minecraft's standard datapack recipe JSON format. Put each recipe in:

```
radlink/data/radlink/recipes/<recipe_id>.json
```

For example, `radlink/data/radlink/recipes/respirator.json` creates a recipe in the `radlink` namespace. The recipe file name is its ID; ingredient and result IDs use the usual `namespace:item_id` format. Custom items defined by RadLink can be used as ingredients or results, for example `radlink:test_food`.

Shaped recipe example (a 3x3 glass recipe for a custom respirator):

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "###",
    "###",
    "###"
  ],
  "key": {
    "#": { "item": "minecraft:glass" }
  },
  "result": { "item": "radlink:respirator", "count": 1 }
}
```

Shapeless recipe example (one golden apple makes one custom food):

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "minecraft:golden_apple" }
  ],
  "result": { "item": "radlink:test_food", "count": 1 }
}
```

RadLink creates a default shapeless recipe using one diamond for each custom item if its recipe file does not exist yet. To provide your own recipe, create `<item_id>.json` in the recipes folder before starting/loading the world; an existing file is kept and used instead of the default. Recipes and their files are included in multiplayer content synchronization.

### Custom commands

Define each command in its own JSON file in the world's datapack:

```
radlink/data/radlink/commands/<command_name>.json
```

The file name (without `.json`) becomes the command name. For example, `customsetweather.json` adds `/customsetweather`. The JSON `name` property is not used.

```json
{
  "permission_level": 2,
  "arguments": [
    {
      "name": "target",
      "type": "player"
    }
  ],
  "actions": [
    {
      "type": "chat_message",
      "say": "Команда выполнена!"
    }
  ]
}
```

`permission_level` is optional and defaults to `0`. Level `2` restricts the command to operators and single-player worlds with cheats enabled. Currently, `player` is the supported argument type; it adds an optional player argument, for example `/customsetweather Steve`. When supplied, that player is passed as the entity context to regular RadLink script actions.

`actions` can contain RadLink script action objects. The `chat_message` action shown above sends its `say` text to the command source. Run `/reload` or `/radlink reload` after changing command JSON files. These files are included in multiplayer content synchronization.

### Custom items and item models

Define ordinary items, food, and projectile items in the root `radlink/items.json` file as a JSON array. IDs are normalized to lowercase, and spaces become underscores; use simple IDs made of letters, digits, and underscores. The result becomes the registry ID `radlink:<id>`. A minimal definition is:

```json
[
  {
    "id": "copper_token",
    "maxStackSize": 64
  }
]
```

The item icon is a Minecraft item model. Put its texture at `radlink/assets/radlink/textures/item/copper_token.png`. RadLink creates a generated item model at `radlink/assets/radlink/models/item/copper_token.json` if one does not already exist. That default model uses the matching texture when present, otherwise it uses the diamond texture. You can also set `"texture": "radlink:item/copper_token"` in the item definition; the value is a resource location, and `radlink:item/copper_token` refers to `assets/radlink/textures/item/copper_token.png`.

To customize the item model, create or edit `assets/radlink/models/item/<id>.json`. For example:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "radlink:item/copper_token"
  }
}
```

This is the standard Minecraft item-model format; it supports normal item-model parents, display transforms, and overrides. RadLink only creates a default model when the file is absent, so a custom model is left in place.

Food fields (`item_type`, `nutrition`, `saturation`, `eat_duration`) and item interaction scripts are described in [Script System Basics](#4-script-system-basics). A thrown item is configured with `"item_type": "projectile"`; it uses the snowball projectile entity and its hit scripts. This JSON option does not define a separate projectile entity, flight animation, or independent projectile model. Its item icon/model is configured like any other item above.

### Custom blocks and block models

Define blocks in the root `radlink/blocks.json` file. Each entry creates both a block and its placeable item, both registered as `radlink:<id>`:

```json
[
  {
    "id": "copper_panel",
    "name": "copper_panel",
    "maxStackSize": 64,
    "hardness": 3.0,
    "resistance": 6.0,
    "directional": false
  }
]
```

Supported block fields are `id`, `name`, `maxStackSize`, `hardness`, `resistance`, and `directional`. Hardness and resistance control breaking and explosion resistance. A normal block uses the same texture on every face. RadLink creates a default cube model, blockstate file, and item model if the corresponding files do not exist. This creates a basic solid block that drops itself; `blocks.json` does not configure block inventories, special drop tables, sounds, or other block behavior. Use a `who: "block"` script in `global.json` for supported script behavior.

For the example above, the default texture path is:

```
radlink/assets/radlink/textures/block/copper_panel.png
```

The generated block model is `assets/radlink/models/block/copper_panel.json`, the blockstate is `assets/radlink/blockstates/copper_panel.json`, and the block's inventory model is `assets/radlink/models/item/copper_panel.json`. These are standard Minecraft resource-pack JSON files. To use a custom block model, put its model JSON in `models/block/` and point the blockstate variant at it. To change how the placed block looks versus its inventory item, edit the blockstate and item model separately.

For a simple cube, the three files can look like this:

`assets/radlink/models/block/copper_panel.json`

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "radlink:block/copper_panel"
  }
}
```

`assets/radlink/blockstates/copper_panel.json`

```json
{
  "variants": {
    "": { "model": "radlink:block/copper_panel" }
  }
}
```

`assets/radlink/models/item/copper_panel.json`

```json
{
  "parent": "radlink:block/copper_panel"
}
```

The names in `blockstates` and model `parent` values are resource locations without `.json`; texture references also omit `.png`. Models exported from Blockbench must use the Minecraft Java block/item model format. Put the exported model JSON in `models/block/` (or `models/item/` for an item), put its PNG texture under `textures/`, and update the blockstate or item model to reference it.

Set `"directional": true` for a block that faces north, south, east, or west when placed. Its default model uses these four textures (up and down both use the top texture):

```
assets/radlink/textures/block/copper_panel_front.png
assets/radlink/textures/block/copper_panel_back.png
assets/radlink/textures/block/copper_panel_side.png
assets/radlink/textures/block/copper_panel_top.png
```

RadLink generates the matching cube model and horizontal-facing blockstate when those files are absent. If you provide your own model or blockstate, it must include the facing variants required by the directional block.

### Custom potion effects

Define effects in the root `radlink/effects.json` file. Each entry registers a potion effect identified as `radlink:<id>`:

```json
[
  {
    "id": "antidote_boost",
    "name": "Antidote Boost",
    "color": "#55CC88",
    "isBad": false
  }
]
```

`color` is a hexadecimal color used by the effect, with or without a leading `#`; `isBad` selects the harmful (`true`) or beneficial (`false`) category. Add its visible name to `assets/radlink/lang/en_us.json` as `"effect.radlink.antidote_boost": "Antidote Boost"`. The effect can be applied by the script `effect` action with `"id": "radlink:antidote_boost"`. Definitions currently set the effect's category and color only; they do not let JSON add custom ticking, damage, or other effect behavior.

### Names, textures, and reloading

Names are supplied by Minecraft language files; the JSON `name` field is not a general-purpose display-name setting. Add translations to `assets/radlink/lang/en_us.json` (or another locale), for example:

```json
{
  "item.radlink.copper_token": "Copper Token",
  "block.radlink.copper_panel": "Copper Panel"
}
```

For blocks, the translation key uses the block definition's `name` value; for items, it uses the item ID. Keep block `name` values simple (for example, `copper_panel`) so they are suitable for a translation key.

All paths above are relative to the world's `radlink/` folder. Use PNG textures and lowercase file names. RadLink preserves existing model and blockstate files rather than regenerating them, so edit or remove a generated file if you want to replace its contents. New items and blocks are registered during game startup: restart Minecraft (and reconnect after the server's content-sync restart notice) after changing definitions. Resource-only changes can be reloaded with Minecraft's resource reload, but a full restart is the safest way to verify them. These files are included in multiplayer content synchronization.

## Custom armor (`items.json` or `armor_item.json`)

Add armor definitions to `items.json` alongside regular items by specifying `slot`; existing definitions in `armor_item.json` continue to work. The `slot` field supports `head`/`helmet`, `chest`/`chestplate`, `legs`/`leggings`, and `feet`/`boots` for wearable player armor. Set `durability` to the exact number of uses before the armor breaks and `defense` to the number of armor points it grants. If `defense` is omitted, the selected material's default is used. Use `horse` (also `horse_armor`) for horse armor; set its separate `protection` value and texture.

```json
[
  {
    "id": "custom_helmet",
    "name": "Custom Helmet",
    "maxStackSize": 1,
    "slot": "head",
    "material": "iron",
    "durability": 250,
    "defense": 3,
    "texture": "radlink:item/custom_helmet"
  },
  {
    "id": "custom_chestplate",
    "name": "Custom Chestplate",
    "maxStackSize": 1,
    "slot": "chest",
    "material": "iron",
    "durability": 250,
    "defense": 8,
    "texture": "radlink:item/custom_chestplate"
  },
  {
    "id": "custom_leggings",
    "name": "Custom Leggings",
    "maxStackSize": 1,
    "slot": "legs",
    "material": "iron",
    "durability": 250,
    "defense": 6,
    "texture": "radlink:item/custom_leggings"
  },
  {
    "id": "custom_boots",
    "name": "Custom Boots",
    "maxStackSize": 1,
    "slot": "feet",
    "material": "iron",
    "durability": 250,
    "defense": 3,
    "texture": "radlink:item/custom_boots"
  },
  {
    "id": "custom_horse_armor",
    "name": "Custom Horse Armor",
    "maxStackSize": 1,
    "slot": "horse",
    "protection": 11,
    "texture": "radlink:item/custom_horse_armor",
    "horseTexture": "radlink:textures/entity/horse/armor/custom_horse_armor.png"
  }
]
```

`texture` is the inventory icon. For player armor, provide armor layer textures in `assets/radlink/textures/models/armor/` using the armor texture name plus `_layer_1.png` and, for leggings, `_layer_2.png`. For horse armor, `horseTexture` is the horse's armor texture; if omitted, it defaults to `assets/radlink/textures/entity/horse/armor/<id>.png`.

---

## 2. Mod Configuration (`radlink-client.toml`)

Located in `.minecraft/config/radlink-client.toml`. Main parameters:

| Parameter | Values | Description |
|-----------|--------|-------------|
| `isMenuChange` | `true` / `false` | Must be `true` to enable the custom main-menu background and button changes. `false` leaves the vanilla menu unchanged. |
| `serverOrWorldName` | `""`, world name, or server address | Selects where the client loads `menu_background.json` and which server's content cache to use. The cache is keyed by host/IP, not port. If empty, the mod searches for the latest world save. |

*Example:*
```toml
isMenuChange = true
serverOrWorldName = "RadLink.aternos.me:29550"
```

For multiplayer synchronization, install the same RadLink version on the client and the Forge server, put definitions/assets under the active server world's `radlink/` folder, and set `serverOrWorldName` to the server's IP/host. The port may be omitted; this is recommended for worlds opened to LAN because Minecraft assigns a new port each time. The server world folder is selected from `server.properties` (`level-name`, default `world`). No HTTP server, extra port, or separate URL is used: files travel over the existing Forge connection. Servers on the same host share one cache, which is updated when content changes.

On the first connection, RadLink compares the server content with the local cache and downloads changed files before Forge synchronizes item/block/effect registries. It then disconnects with a restart notice. Fully close and relaunch Minecraft, then reconnect; simply rejoining without restarting does not register new content. If the cache already matches, the connection continues normally. Only the current content pack is retained per host, so changed ports and updates do not accumulate duplicate packs. A transfer is limited to 1,024 files, 32 MiB per file, and 64 MiB total.

---

## 3. Main Menu Customization (`menu_background.json`)

Allows changing the background (including frame-by-frame animation), modifying button positions and textures, hiding them, or adding new custom buttons.

### Structure
```json
{
    "fps": 20,
    "frames": [
        "bg_0.png",
        "bg_1.png",
        "bg_2.png"
    ],

    "buttons": {
        "singleplayer": {
            "x": 20, "y": 80,
            "width": 180, "height": 30,
            "frames": ["btn_single_0.png", "btn_single_1.png"],
            "hover_frames": ["btn_single_hover_0.png", "btn_single_hover_1.png"],
            "fps": 12
        },
        "multiplayer": {
            "x": 20, "y": 120,
            "width": 180, "height": 30
        },
        "language": {
            "use": "No"
        },
        "accessibility": {
            "use": "No"
        },
        "realms": {
            "use": "No"
        }
    },

    "custom_buttons": [
        {
            "text": "Mod Guide",
            "x": 20, "y": 200,
            "width": 180, "height": 20,
            "url": "https://wiki.example.com",
            "use": "Yes"
        }
    ]
}
```

Buttons can be animated with `frames` and, optionally, a separate `hover_frames` animation. The button's `fps` controls both animations and overrides the top-level background `fps`; if omitted, it uses the background FPS (10 by default). This works for both standard and custom buttons. When a button uses a texture or animation frames, its text is hidden by default; set `"showText": true` to draw the text over the image. Button frame filenames are also searched in `assets/radlink/textures/gui/` inside the selected world's `radlink` folder.

### Standard button keys in `buttons`
* `singleplayer` — Single Player
* `multiplayer` — Multi Player
* `mods` — Mods
* `options` — Options
* `quit` — Quit Game
* `language` — Language Selection / Globe Icon
* `accessibility` — Accessibility Settings / Human Icon
* `realms` — Minecraft Realms

### Parameter `use`
Accepts `"No"`, `"false"` or `"0"` to completely hide the button.

---

## 4. Script System Basics

RadLink scripts are JSON arrays of script definitions. Each definition has:

| Key | Type | Description |
|-----|------|-------------|
| `id` | `string` | Unique identifier for the script/item/block |
| `name` | `string` | Display name |
| `maxStackSize` | `int` | Maximum stack size (for items) |
| `item_type` | `string` | Optional item behavior type. Use `"food"` to make the item edible or `"projectile"` to throw it; if omitted, it is a normal item. |
| `nutrition` | `int` | Food hunger points restored; defaults to `4` for food items. |
| `saturation` | `number` | Food saturation modifier; defaults to `0.3` for food items. |
| `eat_duration` | `int` | Number of ticks required to eat the food; defaults to Minecraft's normal `32` ticks. Must be positive. |
| `trigger` | `string` | Item interaction that starts the script; see the supported trigger list below |
| `if` | `object` | **Main condition block**. Checked at the beginning of event processing, or at projectile impact for projectile items. |
| `then` | `array` or `object` | **Actions**. Executed if the `if` condition is met. |

### Item interaction triggers

Put a `trigger` on an item definition in `items.json`. A matching item script in `global.json` can use the same trigger. Trigger names are case-insensitive; each definition supports one trigger.

| Trigger | When it runs |
|---------|--------------|
| `right_click` | Use the held item when not targeting a block or entity |
| `right_click_block` | Right-click a block with the held item |
| `left_click_block` | Start a left-click interaction with a block (not necessarily a completed block break) |
| `attack_entity` | Attack an entity with the main-hand item |
| `interact_entity` | Right-click an entity with the held item |
| `eated` | Finish eating a food item (runs after the food is consumed) |

Interactions are processed on the server, only if the Forge event was not cancelled. Block/entity triggers use the clicked target's block position for script actions; `right_click` and `eated` use the player's position. The item in the event's hand (or the consumed stack for `eated`) is used to find the matching definition.

```json
[
  {
    "id": "scanner",
    "name": "Scanner",
    "maxStackSize": 1,
    "trigger": "right_click_block",
    "then": [
      { "say": { "text": "Scanned block at the position you clicked." } },
      { "sound": { "id": "minecraft:block.note_block.pling", "volume": 1.0, "pitch": 1.0 } }
    ]
  }
]
```

Food items use Minecraft's normal eating behavior. Add `"item_type": "food"` to make a custom item edible; optional `nutrition` and `saturation` values control how much hunger and saturation it restores, and `eat_duration` sets the eating time in ticks (32 by default). The `eated` trigger runs once the eating finishes, not when the player starts holding right-click:

```json
{
  "id": "test_food",
  "name": "Test Food",
  "maxStackSize": 1,
  "item_type": "food",
  "nutrition": 6,
  "saturation": 0.8,
  "eat_duration": 60,
  "trigger": "eated",
  "then": [
    { "say": { "text": "Food eaten!" } }
  ]
}
```

Projectile items use right-click to launch one copy of their own item stack using the snowball projectile entity and throw sound. One item is consumed per throw, except in creative mode. They do not inherit snowball's built-in damage; actions only run after impact, and entities are damaged only when a `damage` action explicitly targets them. For projectile items, `trigger: "right_click"` identifies the script to run at impact; the script does not run when the projectile is launched. The item icon/model uses the normal item-model files documented above; the JSON definition does not provide a separate projectile entity model or animation:

```json
{
  "id": "test_projectile",
  "name": "Test Projectile",
  "maxStackSize": 64,
  "item_type": "projectile",
  "trigger": "right_click",
  "if": {
    "who": "any",
    "any": { "==": "was_hit" }
  },
  "then": [
    {
      "case": [
        {
          "If": {
            "was_hit": {
              "who": "entity",
              "entity": { "==": ["minecraft:zombie", "minecraft:skeleton"] }
            }
          },
          "Then": { "damage": 5.0 }
        },
        {
          "If": {
            "was_hit": {
              "who": "block",
              "block": { "==": "minecraft:diamond_block" }
            }
          },
          "Then": {
            "say": { "text": "Hit a diamond block!" }
          }
        }
      ]
    }
  ]
}
```

For a projectile's `if` and a nested `was_hit` condition, `who` accepts `"any"` (an entity or block), `"entity"`, or `"block"`. Add the matching `entity` or `block` selector to restrict the hit target. Selectors accept `"any"`, a resource ID such as `"minecraft:diamond_block"` / `"minecraft:blaze"`, an array of IDs, or an object using `==` / `!=`. The action context is the hit entity or block position, so `damage` affects only the entity actually hit.

If `item_type` is absent, RadLink creates a regular item; without a `trigger`, it has no scripted behavior. Supported non-empty `item_type` values are `"food"` and `"projectile"`; any other value is rejected with an error rather than silently creating a different item.

`right_click` does not include block/entity interactions: Forge has separate events for those, so use the corresponding trigger. A click trigger represents an interaction attempt, not necessarily a successful block break or entity attack.

### How triggers and scripts work

1. On startup, `Radlink` reads `items.json`, `blocks.json`, `armor_item.json`, and `effects.json` from the selected world's `radlink` directory and registers the custom content. Item definitions are therefore read when content is registered; restart the game/world after changing what items exist.
2. `ItemUseHandler` listens to Forge's server-side interaction events, identifies the held item, and loads the matching item/global script definition. `global.json` and item scripts are checked separately, so both can run for the same interaction if both definitions match.
3. The script's `trigger` must match the event. Its top-level `if` condition is checked next; only then does `ScriptEngine` execute `then` actions. The same script engine is also used by global tick/death scripts and JSON-defined commands.
4. `WorldTickHandler`, `PlayerDeathHandler`, and `WeatherTracker` provide the existing global world/death/weather paths. `ClientMenuHandler` and `ClientAnimationHandler` handle client-only menu customization and screen overlays; they do not run server gameplay scripts.

To add another server-side item trigger, register a Forge event subscriber in `ItemUseHandler`, pass the event's player, hand's `ItemStack`, and context position to `dispatch`, then document the new exact trigger string in the table above. Client-only input (such as a left click into empty air or a key press) needs a client-to-server network message before it can safely run gameplay actions.

Other useful extensions include player join/leave, successful block breaking, item crafting, sneak-specific clicks, cooldowns, and triggers when an item is equipped or a player takes damage. These need their own Forge event/state handling; they are not currently supported by the item `trigger` field.

### `if` vs `If` / `iF`

| | `if` (lowercase) | `If` / `iF` (mixed case) |
|---|------------------|--------------------------|
| **When checked** | At the **beginning** of event processing | **Inside** the action list (dynamically) |
| **Purpose** | Global gate — decides if the script runs at all | Branching logic inside `then` — similar to if-then-else |
| **Typical use** | Checking entity type, block state, item in hand | Checking variable values during action execution |

Character case is crucial in JSON scripts!

---

## 5. Conditions (`if` / `If`)

Conditions are checked inside `checkConditions()` (main `if`) and `checkNestedCondition()` (nested `If`).

### Periodic world-time events

Use `"who": "time"` with `timeCycles.every` in `global.json` to run a script every set number of in-game days. The first run happens when the world reaches that day count (day 25, then day 50, 75, and so on for an interval of 25). It runs once per matching day and is saved in `variables.json`, so repeated world ticks, dimensions, and server restarts do not repeat it that day.

```json
{
  "if": {
    "who": "time",
    "timeCycles": {
      "every": ["25", "days"]
    }
  },
  "then": [
    {
      "say": "Warning! A sharp deterioration in air quality is expected in the next 24 hours. Wearing respiratory protection is recommended."
    }
  ]
}
```

`every` must contain a positive whole number and the unit `"day"` or `"days"`. For other time conditions, the existing `timeCycles.now` form (for example `"isNewDay"`) remains available. The `say` action accepts either a plain string or the existing `{ "text": "..." }` object.

### `query` vs `calc` — What's the Difference?

Both are used to check variable values, but they serve different purposes:

| | `query` | `calc` |
|---|---------|--------|
| **Purpose** | Simple comparison of a variable against a value | Evaluate a mathematical expression, then compare the result |
| **Math operations** | Comparison operators only (`>=`, `>`, `<=`, `<`, `==`, `!=`, range) | Full arithmetic (`+`, `-`, `*`, `/`, `%`, `^`, parentheses, `multiply`, etc.) |
| **How variables are used** | Variable names are **object keys** | Variables are inserted inside the **expression string** via `${...}` |
| **Best for** | Quick threshold checks | Complex formulas where you must calculate first, then compare |

> *"If `query` seems too simple for you, you can use `calc` with advanced functions such as multiplication, division, addition, subtraction, exponentiation, etc."*

#### `query` — Simple Variable Check

Compares a world variable directly with a number or operator object.

**Form 1 — Object (single variable):**
```json
{ "query": { "global_atmosphere": { ">=": 10 } } }
```

**Form 2 — Array (computed value vs threshold):**
```json
{ "query": [
    { "random": [{ "from": 0 }, { "to": 1 }, { "numbers_after_comma": 2 }] },
    { "<=": 0.05 }
] }
```

*Examples from `global.json`:*
```json
{ "query": { "global_atmosphere": { ">": 100 } } }
```
```json
{ "query": { "entity_disease_progress": { ">": 0 } } }
```

#### `calc` — Mathematical Expression Evaluation

Evaluates an expression string (with `${variable}` placeholders substituted) and compares the result.

**Basic form:**
```json
{ "calc": { "expression": "${global_atmosphere} / 2", ">=": 100 } }
```

**With nested math in the comparison value:**
```json
{ "calc": { "global_atmosphere": { ">=": { "multiply": [5, 2] } } } }
```

*Example from `items.json`:*
```json
{ "calc": { "expression": "${global_atmosphere} / 2", ">=": 100 } }
```

**Supported math in `calc` / `evaluateMath()`:**
* Operators: `+`, `-`, `*`, `/`, `%` (modulo), `^` (power)
* Parentheses: `(`, `)`
* Unary plus/minus

### Complete Condition Reference

| Condition | Type | Example | Description |
|-----------|------|---------|-------------|
| `who` | `string` | `"entity"` or `"block"` | Defines context for world-tick scripts |
| `block` | `object` | `{ "block": { "==": "minecraft:furnace" } }` | Matches a specific block ID (used with `who: "block"`) |
| `lit` | `boolean` | `{ "lit": true }` | Checks if block state has `lit` property |
| `entity` | `object` / `array` | `{ "entity": { "==": "player" } }` | Checks entity type (`==` or `!=`) |
| `item` | `object` / `string` | `{ "item": { "==": "minecraft:diamond" } }` | Checks item in player's main hand |
| `query` | `object` / `array` | `{ "query": { "global_atmosphere": { ">=": 10 } } }` | Simple variable comparison |
| `calc` | `object` | `{ "calc": { "expression": "${x} + 1", ">=": 5 } }` | Math expression evaluation + comparison |
| `timeCycles` | `object` | `{ "timeCycles": { "now": "isNewDay" } }` | Checks if a new Minecraft day has started |
| `weather` | `string` | `{ "weather": "clear" }` | Checks weather: `clear`, `rain`, `thunder` |
| `time_check` | `object` | `{ "time_check": { "ago": "second", "time": 100 } }` | Checks time since rain started |
| `var` | `object` | `{ "var": "${heat}", ">": 100 }` | Shorthand variable check (nested `If`) |
| `super` | `boolean` | `{ "super": true }` | Marks script as "super" priority |

**Comparison operators supported in condition values:**
* `>=`, `>`, `<=`, `<` — numeric comparisons
* `==`, `!=` — equality / inequality
* Array form `[ { "from": 0 }, { "to": 100 } ]` — range check (value is between `from` and `to` inclusive)

---

## 6. Actions (`then` / `Then`)

Actions are executed when conditions pass. They can be a single object or an array of objects.

| Action | Type | Example | Description |
|--------|------|---------|-------------|
| `explosion` | `object` | `{ "explosion": { "radius": 4.0, "fire": false } }` | Creates an explosion at entity/block position |
| `summon` | `object` | `{ "summon": { "type": "minecraft:zombie", "count": 1 } }` | Spawns an entity |
| `damage` | `number` | `{ "damage": 5.0 }` | Deals magic damage to the entity |
| `effect` | `object` | `{ "effect": { "id": "minecraft:regeneration", "duration": 100, "amplifier": 1 } }` | Applies a potion effect |
| `command` | `string` | `{ "command": "give @s minecraft:diamond 1" }` | Executes a server command |
| `sound` | `object` | `{ "sound": { "id": "minecraft:entity.experience_orb.pickup", "volume": 1.0, "pitch": 1.0 } }` | Plays a sound |
| `particle` | `object` | `{ "particle": { "type": "minecraft:flame", "count": 10, "speed": 0.1 } }` | Spawns particles |
| `say` / `message` | `object` | `{ "say": { "text": "Atmosphere: ${global_atmosphere}" } }` | Sends chat message to all players (supports `${variables}`) |
| `add` | `object` | `{ "add": { "global_atmosphere": 1.0 } }` | Adds value to a variable (if result ≤ 0, variable is removed) |
| `set` | `object` / `array` | `{ "set": { "has_right_arm": 3.0 } }` | Sets a variable value |
| `set` (blocks) | `array` | See below | Sets a single block or fills an area |
| `tp` | `object` | See below | Teleports an entity |
| `run` | `array` | `{ "run": [ { "command": "..." }, { "command": "..." } ] }` | Runs multiple actions in sequence |

### `set` — Blocks (Single Block or Fill Area)

**A) Single block (`mode_type: "set_block"`):**
```json
{
    "set": [
        {
            "mode": [
                { "mode_type": "set_block" },
                { "coordinates_1": [{ "~": "0" }, { "~": "1" }, { "~": "0" }] }
            ],
            "block": "minecraft:fire"
        }
    ]
}
```

**B) Fill area (`mode_type: "fill"`):**
```json
{
    "set": [
        {
            "mode": [
                { "mode_type": "fill" },
                { "coordinates_1": [{ "~": "-1" }, { "~": "-1" }, { "~": "-1" }] },
                { "coordinates_2": [{ "~": "1" }, { "~": "-1" }, { "~": "1" }] }
            ],
            "block": "minecraft:diamond_block",
            "harvestLevel": 2,
            "tests": [
                { "isAir": false, "type": "destroy", "filter": "white_wool" }
            ]
        }
    ]
}
```

**Coordinate syntax:** `"~"` means current position; `{ "~": "-1" }` means current + offset.

`harvestLevel` is optional and applies when `tests` sets `"type": "destroy"`: it selects the tool tier for drops (0 wooden, 1 stone, 2 iron, 3 diamond, 4 or higher netherite). If the block requires a better tool, it is not destroyed. Blocks that do not require a specific tool can still be destroyed at any tier, as in vanilla Minecraft. Without `harvestLevel`, the block is destroyed using the normal script behavior. `filter` may be a block ID to restrict affected blocks; `"no"`, `"none"`, or `"any"` means no block-ID filter. For projectile-hit scripts, use `was_hit` with `who: "block"` to run a block action only when the projectile hits a block.

Minecraft block, item, and entity IDs may omit the namespace: `"dirt"` is treated as `"minecraft:dirt"`. IDs with an explicit namespace, such as `"modid:custom_block"`, are matched as written.

**Fill `tests` fields:**
* `isAir` — `true`/`false`, only modify blocks matching air condition
* `type` — `destroy`, `keep`, `replace`
* `filter` — block ID filter (only blocks matching this ID are affected)
  
### `tp` — Teleport Entity

**A) Simple teleport:**
```json
{
    "tp": {
        "mode": [
            { "mode_type": "teleport" },
            { "coordinates": [{ "~": "0" }, { "~": "1" }, { "~": "0" }] }
        ]
    }
}
```

**B) Teleport with return after delay (`revive_teleport`):**
```json
{
    "tp": {
        "mode": [
            {
                "mode_type": "revive_teleport",
                "coordinates": [500, { "~": "15" }, 200],
                "return_delay_ticks": 60
            }
        ]
    }
}
```
*1 tick = 1/20 second. `return_delay_ticks: 60` = 3 seconds.*

---

## 7. Variables and Placeholders

### Variable Types

| Type | Prefix | Scope | Persistence |
|------|--------|-------|-------------|
| **Global** | `global_` | Entire world/server | Saved in `variables.json`, survives player death and logout |
| **Player** | (none) | Bound to player UUID | Saved per-player |
| **Block** | (none) | Bound to dimension + X,Y,Z | Saved per-block location |

### Placeholders in Text

Use `${variable_name}` inside any text field (`say`, `message`, command text, etc.) to insert the current variable value at runtime.

```json
{ "say": { "text": "Current atmosphere: ${global_atmosphere}" } }
```

Placeholder syntax also supports `(variable_name)`:
```
${global_atmosphere}   or   (global_atmosphere)
```

### `variables.json`

This file is managed automatically by the mod. You generally do not need to edit it manually, but its structure is:

```json
{
    "global_atmosphere": 150.0,
    "global_last_day": 12.0,
    "heat_<dimension>_<x>_<y>_<z>": 45.0,
    "entity_disease_progress_<UUID>": 3.0
}
```

---

## 8. Branching Logic (`case`)
```json
[
    {
        "id": "atmosphere_checker",
        "name": "atmosphere_checker",
        "maxStackSize": 1,
        "trigger": "right_click",
        "then": [
            {
               "If": { "entity": { "==": "player" } },
               "Then": [
                   {
                      "case": [
                         {
                            "If": { "query": { "global_atmosphere": { ">=": 100 } } },
                            "Then": [
                               { "say": { "text": "Critical atmospheric level!" } }
                            ]
                         },
                         {
                            "If": { "query": { "global_atmosphere": { ">=": 50 } } },
                            "Then": [
                               { "say": { "text": "Atmosphere is normal." } }
                            ]
                         }
                      ]
                   }
               ]
            }
        ]
    }
]
```