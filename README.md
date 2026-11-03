# ✦ Stella Client

A purple-themed, DonutSMP-focused **Fabric** client mod for **Minecraft Java 1.21.4**.

| | |
|---|---|
| Minecraft | 1.21.4 |
| Fabric Loader | ≥ 0.16.10 |
| Fabric API | 0.119.4+1.21.4 |
| Java | 21 |

## Installation

1. Install **Fabric Loader** for Minecraft 1.21.4 (https://fabricmc.net/use/installer/).
2. Download **Fabric API 0.119.4+1.21.4** (or newer for 1.21.4) and put it in `.minecraft/mods`.
3. Download `stella-client-1.0.0.jar` from the latest **Build Stella Client** GitHub Actions run
   (artifact `stella-client-jar`) or build it yourself (below), and put it in `.minecraft/mods`.
   Use the jar **without** `-sources` in its name.
4. Launch the *Fabric Loader 1.21.4* profile. Press **Right Shift** to open the ClickGUI.

### Building from source

```bash
./gradlew build          # jar is written to build/libs/stella-client-1.0.0.jar
./gradlew runClient      # launches a development client
```

Every push is compiled by GitHub Actions. A second job boots the real game under a virtual display,
force-applies every mixin, opens the ClickGUI and HUD editor, creates a test world with every module
enabled, takes screenshots, and fails if anything errors.

## Controls

| Action | Default |
|---|---|
| Open / close ClickGUI | **Right Shift** (change it in the THEMES panel → *GUI Key*) |
| Toggle a module | Left-click its row |
| Module settings | Click the ⚙ gear or right-click the row |
| Collapse a panel | Right-click the panel header |
| Search | Click the search bar, press **Ctrl+F**, or just start typing |
| HUD editor | THEMES panel → *HUD Editor*, or the *HUD Editor* module keybind |
| Profiles | **CONFIGS** button at the bottom centre |
| Zoom | Hold **C** |
| Freecam | **V** (toggle) |
| Free Look | Hold **Left Alt** |
| Auto Relog emergency stop | **End** |
| Interact with the Block Notifier list | Open chat, then click it |

Every module has its own keybind setting (click the value, press a key; right-click clears it).

## Modules

**Combat:** Player ESP (hitbox outline, optional fill, tracers from a configurable screen point, names,
distance, health, each with its own settings), Player Detector.

**Misc:** Auto Relog, Staff List, Staff Spectator Alerts, Freecam (press **V** to fly the camera away
from your player, who stays put), Free Look (hold **Left Alt** to look around without turning your head,
in third or first person).

**Render:** Storage ESP (chests, trapped chests, barrels, shulkers, ender chests, hoppers, dispensers,
plus your own block list, with optional tracers), Block ESP (with optional tracers), Sus Chunk Finder
(red box over each suspected chunk, with Column, Flat or Full Height modes, reasons, confidence and
optional tracers), Waypoints.

**World:** Block Notifier, Amethyst Detector, Weather Notifier (alerts when rain or thunder starts or stops).

**Visuals:** Custom FOV, Custom Crosshair (with presets and a live preview in its settings), Custom Sky
(Cosmic Stella, Lavender Sunset, Blood Moon, Arctic Aurora, Midnight, Custom), Fullbright, Ambience,
Motion Blur, Fog Customizer, Weather Customizer, Hurt Camera, No Pumpkin Overlay, Zoom, World Time,
Item Physics, Enchantment Glint, Damage Tint.

**Client:** ClickGUI (theme and GUI key), HUD Editor.

**HUD (one module per element):** Stella Branding, Keystrokes (+CPS), FPS, TPS, Coordinates, Ping,
Region Map, Notifications, Staff List, Block Notifier list, Player Detector list. Each element has its
own toggle, position, scale, opacity and colours, and moving or disabling one never affects another.
Turning any module on or off shows a small popup; switch this off with *Module Toggle Popups* in the
Notifications HUD settings.

**Settings:** Config (auto-save), Animations.

## Configuration

Everything lives in `.minecraft/config/stella/`:

| File | Contents |
|---|---|
| `client.json` | Active profile name |
| `profiles/<name>.json` | Module on/off states, every setting, HUD positions and scales, GUI panel layout, keybinds, theme |
| `exports/` | Profiles exported from the GUI |
| `staff.json` | **Your** verified staff roster (ships empty) |
| `regions.json` | Region Map grid (ships as a flagged placeholder) |
| `waypoints.json` | Waypoints created from Block Notifier |

The **CONFIGS** button at the bottom of the ClickGUI handles profiles. *New* copies the current profile.
*Delete* deletes the profile whose name you type into the box. *Save* and *Export* write files
(*Export* also copies the profile to the clipboard). *Import* reads a profile from the clipboard.
*Reset* needs a second click to confirm. Changes are auto-saved every few seconds and again when you quit.

## What this client can and cannot know

Stella only uses information the server actually sends to your client. These are its limits:

* **Block Notifier, Block ESP, Storage ESP, Amethyst Detector and Sus Chunk Finder** can only see chunks your client
  has received. Nothing is ever reported for chunks you have never loaded. Block Notifier remembers each
  position once per session, so a chunk that reloads does not create a duplicate.
* **Sus Chunk Finder** gives hints, not proof. Each red chunk shows its reasons and a confidence level.
  Temples, trial chambers, mineshafts and geodes can all cause false positives. Sideways deepslate is the
  strongest signal, because world generation always places deepslate vertically.
* **Staff List** has no built-in staff data. Add names you have verified yourself to `staff.json`, then
  toggle *Reload staff.json*. "In tab" means the name is in the tab list. If a staff member is missing
  from the tab list, the client cannot tell whether they are offline or vanished.
* **Staff Spectator Alerts** can only report evidence it can observe:
  * a verified staff member's tab entry shows spectator mode;
  * a spectator-mode player entity is nearby;
  * a player entity is nearby but missing from the tab list;
  * an invisible verified staff member is nearby.

  Most vanish plugins send nothing at all, so a normal client cannot detect fully vanished staff.
  **No alert does not mean nobody is watching.**
* **Region Map** is a 9×9 grid of 50,000-block cells spanning −225k…+225k on X and Z. The layout comes
  from two references you supplied: an in-game screenshot (cell IDs and colours) and a region diagram
  (region shapes and geometry). The cell IDs were read from a low-resolution image, so correct any that
  differ in `regions.json`, then toggle *Reload regions.json*. The *Numbering* setting switches between the
  in-game IDs and plain 1–81 numbering. The STAFF section uses your verified `staff.json` plus tab-list data.
* **TPS** is estimated from world-time packets. It shows `...` until it has enough samples, and it is
  affected by network jitter.
* **Player health** is whatever the server sends. Many servers hide or fake other players' health.
* **Tracers** are drawn as real 3D lines in screen pixels, so they stay thin and smooth at any GUI scale.
* **Freecam** is client-side only. Your real player stays where you left it, and the server can still
  see it. **Weather Notifier** reports changes as the server sends them; servers send no forecasts.
* **World Time, Weather, Custom Sky, Ambience and Fog** are visual only. They do not change anything
  on the server.
* **Auto Relog** connects through the normal login flow. It does not bypass authentication, queues,
  whitelists or bans. It only digs **downward**. It stops for fluids next to the next block, bedrock or
  unbreakable blocks, drops larger than the configured limit, the void or world bottom, low health,
  damage, lava or fire, and the emergency-stop key. **Check the server's rules first: many servers,
  possibly including DonutSMP, forbid automation or certain client modifications.** You are responsible
  for how you use this mod.
* **Enchantment Glint** can hide the glint and change its speed and strength. Its colour comes from the
  vanilla texture and cannot be changed.

## Project layout

```
src/main/java/dev/stella/client/
  StellaClient.java        entrypoint, event wiring
  module/  setting/        module + typed setting system
  modules/<category>/      every feature, one class each
  hud/                     HudElement base, manager, HUD editor
  gui/                     ClickGUI (panels, settings windows, side panel, themes)
  config/                  profile persistence, GUI layout
  scan/                    shared incremental chunk scanner
  render/                  batched 3D boxes, 2D helpers, world→screen projection
  data/                    staff roster, region grid, waypoints
  notification/            toast queue
  mixin/                   Minecraft hooks
```

## Credits

* UI font: **TNH Xuong** by Truong Nguyen Huy / Moc Studio, licensed under the SIL Open Font License 1.1
  (`src/main/resources/assets/stella/font/XUONG-OFL.txt`). Toggle it in the THEMES panel → *Custom Font*.
