# RadLink Mod & Script Engine Documentation

This mod allows you to create custom items, events, blocks, as well as fully customize the game's Main Menu using a flexible system of JSON configurations and scripts.

---

## 1. Folder Structure and File Location

All script and menu configuration JSON files are stored in the game folder and can be linked to a specific world or server.

### A) For Single-Player Worlds
`run/saves/<World_Name>/radlink/`

### B) For Servers (Menu Background and Buttons for Specific IP)
`run/radlink/servers/<Server_IP_without_dots>/`

*Example for play.example.com:*
`run/radlink/servers/play_example_com/menu_background.json`

```text
├── assets/              <-- folder for custom resources and textures
├── data/                <-- generation data and data packs
├── armor_item.json      <-- settings and scripts for armor items
├── blocks.json          <-- behavior and event scripts for blocks
├── effects.json         <-- registration and parameters for effects
├── global.json          <-- global events and world tickers
├── items.json           <-- item scripts (including right_click)
├── menu_background.json <-- customization of the main menu and buttons
├── variables.json       <-- dynamic variable storage
└── pack.mcmeta          <-- metadata datapack/resource pack
```

---

## 2. Mod Configuration (radlink-client.toml)

In the folder `run/config/radlink-client.toml` the following main parameters are available:

* **`isMenuChange = true / false`**
  * `true`: Enables custom menu background and button reconfiguration.
  * `false`: Completely disables all menu changes (the game maintains 100% vanilla appearance, logo, panorama, and standard buttons).

* **`serverOrWorldName = ""`**
  * Allows forcing the mod to take background and menu settings (`menu_background.json`) from a specific world or server.
  * *Example for a world:* `"MyAwesomeWorld"`
  * *Example for a server:* `"play.example.com"`
  * *Note:* If left empty (`""`), the mod automatically loads data from the last launched world.

---

## 3. Menu Customization (menu_background.json)

Allows changing the background (including GIF-like animation), modifying button positions and textures, hiding them, or adding new ones.

### Structure of menu_background.json

```json5
{
    "fps": 20,                          // Animation speed of the background (frames per second)
    "frames": [                         // Animation frames of the background (PNG files in the folder)
        "bg_0.png",
        "bg_1.png",
        "bg_2.png"
    ],
    // Instead of "frames" you can specify a static background: "texture": "bg.png",

    "buttons": {                        // Redefinition of vanilla buttons
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
        "language": {                     // Icon of the language (globe)
            "use": "No"                     // Completely hide the button
        },
        "accessibility": {                // Icon of accessibility (human figure)
            "use": "No"                     // Completely hide the button
        },
        "realms": {
            "use": "No"                     // Hide the Realms button
        }
    },

    "custom_buttons": [                 // Additional custom buttons
        {
            "text": "Mod Guide",
            "x": 20, "y": 200,
            "width": 180, "height": 20,
            "url": "https://wiki.example.com", // Opens link in browser
            "use": "Yes"
        }
    ]
}
```

### Keys of standard buttons in "buttons":
* `singleplayer` (Single Player)
* `multiplayer` (Multi Player)
* `mods` (Mods)
* `options` (Options)
* `quit` (Quit Game)
* `language` (Language Selection / Globe Icon)
* `accessibility` (Accessibility Settings / Human Icon)
* `realms` (Minecraft Realms)

### Parameter "use":
* Accepts values `"No"`, `"false"` or `"0"` to completely hide the button.

---

## 4. Difference between "if" and "If" in scripts

The case of characters is crucial when creating JSON scripts!

* **`"if"` (Main block of conditions):**
  * Checked **at the beginning** of event processing.
  * Determines whether the script will run at all.
  * Contains global context (`"who": "entity" / "block"`, item check in hand `"item"`, time cycles `"timeCycles"`, etc.).

* **`"If"` / `"iF"` (Nested condition inside "then"):**
  * Checked **inside** the list of actions (dynamically).
  * Used for branching logic (similar to if-then-else construct).
  * Executes a specific action `"Then"` only when the condition is met (e.g., checking a variable via `"query"` or block state).

---

## 5. Conditions for Script Checking ("if" / "If")

* `"entity": "player" | "zombie"` — checking the type of entity.
* `"item": "minecraft:diamond"` — checking the item in the main hand.
* `"lit": true | false` — checking the state of the block (lit/unlit).
* `"query": { "global_atmosphere": { ">=": 10 } }` — checking the values of variables.
* `"timeCycles": { "now": "isNewDay" }` — checking the occurrence of a new day.

---

## 6. Actions for Script Execution ("then" / "Then")

* **explosion** — Creating an explosion (at the location of the entity or block):
  ```json
  { "explosion": { "radius": 4.0, "fire": false } }
  ```

* **summon** — Summoning an entity:
  ```json
  { "summon": { "type": "minecraft:zombie", "count": 1 } }
  ```

* **damage** — Creating magical damage to an entity:
  ```json
  { "damage": 5.0 }
  ```

* **effect** — Applying a potion effect:
  ```json
  { "effect": { "id": "minecraft:regeneration", "duration": 100, "amplifier": 1 } }
  ```

* **sound** — Playing a sound:
  ```json
  { "sound": { "id": "minecraft:entity.experience_orb.pickup", "volume": 1.0, "pitch": 1.0 } }
  ```

* **particle** — Spawning particles:
  ```json
  { "particle": { "type": "minecraft:flame", "count": 10, "speed": 0.1 } }
  ```

* **say / message** — Sending a message in chat (supports placeholders `${variables}`):
  ```json
  { "say": { "text": "Current atmosphere: ${global_atmosphere}" } }
  ```

* **add / set (variables)** — Changing or setting numerical values:
  ```json
  { "add": { "global_atmosphere": 1.0 } }
  ```

* **set (blocks)** — Setting a single block or filling (fill) an area:

  *а) Single block (`mode_type: "set_block"`):*
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

  *б) Filling the area (`mode_type: "fill"`):*
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

---

## 7. Variables and Placeholders

* **Global Variables:**
  Variables with the prefix `global_` (e.g., `${global_atmosphere}`) are saved for the entire world inside the `variables.json` file. They don't reset when the player dies or leaves the server.

* **Player Variables:**
  Regular variables are automatically bound to the player's unique UUID or to the specific coordinates of the block that triggered the event.

* **Placeholders in Chat:**
  You can dynamic call any variable inside text lines using the syntax `${variable_name}`. It will be replaced with its actual numeric or string value in real-time.

---

### 8. EXAMPLE OF A COMPLETE JSON SCRIPT FOR AN ITEM (items.json) 
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