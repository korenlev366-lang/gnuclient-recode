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
- Singletons injected by the wrapper: `client`, `world`, `status`, `inventory`, `keybinds`, `packets`, `lenience`, `util`, `modules`, plus `Mc`.

## Scripts included (50)

### Combat
VelocityAlert, AutoSprint, NoFall, Criticals, AutoSoup, HurtTimeTracker, KnockbackDelay, AntiVoid, AutoLeave, SprintDirection, WTap, HitLog

### Movement
AutoJump, InventoryMove, NoSlow, Step, AirStrafe, LongJump, Spider, BoatFly, Jesus, Speed, Freeze, NoRotate

### Utility
AutoGG, AutoRejoin, Spammer, AutoArmor, KeepAliveSpoofer, ChatFilter, NoRotateSetback, AutoClicker, AutoRightClick, Disabler, Timer, Blink

### Visual / HUD
CoordinatesHUD, SpeedHUD, HealthHUD, TimerDisplay, PacketCounter, DirectionHUD, EntityCountHUD

### World
NetherCoordinateDisplay, LightningAlert, PlayerProximityAlert, AutoTool

### Fun / Misc
Spin, CustomNoSlow, BlinkFly
