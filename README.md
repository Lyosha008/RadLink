# RadLink Mod & Script Engine Documentation

RadLink is a Minecraft Forge mod (1.20.2, Forge 48.1.0) that lets you create custom items, events, blocks, armor, effects, and fully customize the game's Main Menu using flexible JSON configurations and a built-in script engine.

---

## Table of Contents

1. [Folder Structure and File Locations](#1-folder-structure-and-file-locations)
2. [Mod Configuration (`radlink-client.toml`)](#2-mod-configuration-radlink-clienttoml)
3. [Main Menu Customization (`menu_background.json`)](#3-main-menu-customization-menu_backgroundjson)
4. [Script System Basics](#4-script-system-basics)
5. [Conditions (`if` / `If` / `iF`)](#5-conditions-if--if--if)
   - [`query` vs `calc` — What's the Difference?](#query-vs-calc--whats-the-difference)
   - [Complete Condition Reference](#complete-condition-reference)
6. [Actions (`then` / `Then`)](#6-actions-then--then)
7. [Variables and Placeholders](#7-variables-and-placeholders)
8. [Branching Logic (`case`)](#8-branching-logic-case)
9. [Complete Script Examples](#9-complete-script-examples)
10. [Tips and Troubleshooting](#10-tips-and-troubleshooting)

---

## 1. Folder Structure and File Locations

All script and menu configuration JSON files are stored inside the game folder and can be linked to a specific single-player world or a multiplayer server.

### A) Single-Player Worlds
```
run/saves/<World_Name>/radlink/
```

### B) Servers (Menu Background and Buttons for Specific IP)
```
run/radlink/servers/<Server_IP_without_dots>/
```
*Example for `play.example.com`:*
```
run/radlink/servers/play_example_com/menu_background.json
```

### Available files inside `radlink/`

| File | Purpose |
|------|---------|
| `assets/` | Custom resources and textures |
| `data/` | Generation data and data packs |
| `armor_item.json` | Settings and scripts for custom armor items |
| `blocks.json` | Behavior and event scripts for custom blocks |
| `effects.json` | Registration and parameters for custom potion effects |
| `global.json` | Global events and world tickers |
| `items.json` | Item scripts (including `right_click` triggers) |
| `menu_background.json` | Customization of the main menu and buttons |
| `variables.json` | Dynamic world variable storage |
| `pack.mcmeta` | Metadata for datapack / resource pack |

---

## 2. Mod Configuration (`radlink-client.toml`)

Located in `run/config/radlink-client.toml`. Main parameters:

| Parameter | Values | Description |
|-----------|--------|-------------|
| `isMenuChange` | `true` / `false` | `true` enables custom menu background and button reconfiguration. `false` disables all menu changes (100% vanilla appearance). |
| `serverOrWorldName` | `""` or name | Forces the mod to load `menu_background.json` from a specific world or server. If empty (`""`), data is loaded from the last launched world automatically. |

*Example:*
```toml
isMenuChange = true
serverOrWorldName = "MyAwesomeWorld"
```

---

## 3. Main Menu Customization (`menu_background.json`)

Allows changing the background (including frame-by-frame animation), modifying button positions and textures, hiding them, or adding new custom buttons.

### Structure
```json5
{
    "fps": 20,
    "frames": [
        "bg_0.png",
        "bg_1.png",
        "bg_2.png"
    ],
    // Alternative to "frames" — static background:
    // "texture": "bg.png",

    "buttons": {
        "singleplayer": {
            "x": 20, "y": 80,
            "width": 180, "height": 30,
            "texture": "btn_single.png",
            "hover_texture": "btn_single_hover.png"
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
| `trigger` | `string` | Event that triggers the script (e.g., `"right_click"`) |
| `if` | `object` | **Main condition block**. Checked at the beginning of event processing. Determines whether the script runs at all. |
| `then` | `array` or `object` | **Actions**. Executed if the `if` condition is met. |

### `if` vs `If` / `iF`

| | `if` (lowercase) | `If` / `iF` (mixed case) |
|---|------------------|--------------------------|
| **When checked** | At the **beginning** of event processing | **Inside** the action list (dynamically) |
| **Purpose** | Global gate — decides if the script runs at all | Branching logic inside `then` — similar to if-then-else |
| **Typical use** | Checking entity type, block state, item in hand | Checking variable values during action execution |

Character case is crucial in JSON scripts!

---

## 5. Conditions (`if` / `If` / `iF`)

Conditions are checked inside `checkConditions()` (main `if`) and `checkNestedCondition()` (nested `If`/`iF`).
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
            "tests": [
                { "isAir": false, "type": "destroy", "filter": "white_wool" }
            ]
        }
    ]
}
```

**Coordinate syntax:** `"~"` means current position; `{ "~": "-1" }` means current + offset.

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
                    "summon": {
                        "type": "minecraft:zombie",
                        "count": 1
                    }
                },
                { "damage": 5.0 },
                {
                    "effect": {
                        "id": "minecraft:regeneration",
                        "duration": 100,
                        "amplifier": 1
                    }
                },
                { "say": { "text": "Atmosphere: ${global_atmosphere}" } },
                {
                    "explosion": {
                        "radius": 3.0,
                        "fire": false
                    }
                }
            ]
            }
        ]
    }
]
```

### 9. Branching logic ("case")

  Allows you to check a chain of conditions in turn and execute only the `Then` action that first matches the `If` conditions..

  ```json
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
```