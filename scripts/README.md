# GNUClient example scripts

Bare-body Java scripts for the runtime `ScriptManager`.

## Install

Copy any `.java` file into:

```
~/.config/gnuclient/scripts/
```

Reload with **Right Shift + R** in-game (or restart the client).

Each file becomes a module under the **Scripts** category in the ClickGUI.

## Notes

- Do **not** add `package` / `class` declarations — `ScriptManager` wraps the body in a generated `Module` subclass.
- Register settings inside `onLoad()` so they exist before config load.
- `onOverlay` / `onRender` must be `public` to override `Module` draw hooks.
- Singletons: `client`, `world`, `status`, `inventory`, `keybinds`, `packets`, `lenience`, `util`, `draw`, `shared`, `hud`, plus per-script `modules` / `commands`, and `Mc`.

## Ultra-custom API

### Cross-module (`modules`)
- Own settings: `register*` / `get*` / `setButton` / `setSlider` / `setMode` / `bind`
- Other modules: `names()`, `exists`, `isEnabled`, `enable` / `disable` / `toggle`
- Foreign settings: `getBool` / `setBool` / `getSlider` / `setSlider` / `getMode` / `setMode` / `settingNames`

### Visuals (`draw` + `hud`)
- World: `beginWorld` / `endWorld`, `box`, `boxOutline`, `line`, `entityBox`, `tracer` inside `public void onRender(float)`
- HUD: `draw.text(...)` inside `public void onOverlay(Object)`
- Toasts: `hud.notify("title")`

### Script bus (`shared`)
- `put` / `get` / `getString` / `getBool` / `getFloat` / `getInt` / `emit`
- Optional hook: `void onShared(String channel, Object payload)`

### Commands (`commands`)
- `commands.register("name")` in `onLoad`
- `String|boolean onCommand(String name, String[] args)` — return a reply string, `true`, or `null` (unhandled)
- Invoked as client-only `.name ...` chat (packet cancelled)

### Extra combat/motion hooks
- `void onAttack(Object entity)`
- `void onStrafe(float forward, float strafe)`
- `void onJump()`

### Packet / inventory / client (earlier extensions)
Chat text, PosLook rewrite, keepalive/transaction mutation, reconnect, armor equip, best tool, soup checks, GUI movement — see table below.

| Area | Methods |
|------|---------|
| Chat | `packets.chatText`, `isChatReceive` |
| PosLook | `packets.setPosLookRotation` |
| KeepAlive | `packets.keepAliveId` / `setKeepAliveId` |
| Inventory | `setSlot`, `isSoup`, `equipBestArmor`, `findBestHotbarTool` |
| Client | `hasScreen`, `rememberServer`, `reconnectToLastServer` |

## Showcase scripts
- **ModuleController** — `.mod` / `.mods`, auto-disable KillAura at low HP
- **PlayerEsp** — world boxes + tracers via `draw`
- **SharedBus** — `.ping` / `.bus` + `onShared`
- **AttackHook** — `onAttack` + toast/chat

## Scripts included

### Combat
VelocityAlert, AutoSprint, NoFall, Criticals, AutoSoup, HurtTimeTracker, KnockbackDelay, AntiVoid, AutoLeave, SprintDirection, WTap, HitLog, AttackHook, ModuleController

### Movement
AutoJump, InventoryMove, NoSlow, Step, AirStrafe, LongJump, Spider, BoatFly, Jesus, Speed, Freeze, NoRotate

### Utility
AutoGG, AutoRejoin, Spammer, AutoArmor, KeepAliveSpoofer, ChatFilter, NoRotateSetback, AutoClicker, AutoRightClick, Disabler, Timer, Blink, SharedBus

### Visual / HUD
CoordinatesHUD, SpeedHUD, HealthHUD, TimerDisplay, PacketCounter, DirectionHUD, EntityCountHUD, PlayerEsp

### World
NetherCoordinateDisplay, LightningAlert, PlayerProximityAlert, AutoTool

### Fun / Misc
Spin, CustomNoSlow, BlinkFly
