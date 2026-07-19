# Ultra-custom Script API — Design

Date: 2026-07-19

## Goal

Make GNUClient scripts highly customizable: control other modules, share state
across scripts, draw custom visuals, register chat commands, and react to
combat/motion events — without rewriting the client.

## Approach

Thin facades over existing systems (`ModuleManager`, `EspDraw`/`RenderHelper`,
`PacketEvents`, `ChatCommandHandler`, Forge attack/strafe/jump posts).

## Surface

| Facade | Capability |
|--------|------------|
| `modules` | Own settings + enable/read/write any module settings |
| `draw` / `hud` | World ESP/tracers/HUD text + toasts |
| `shared` | Global KV store + `emit` / `onShared` |
| `commands` | `.name` client commands → `onCommand` |
| hooks | `onAttack`, `onStrafe`, `onJump` (+ existing tick/packet/render) |

## Non-goals (deferred)

- New ClickGUI setting widgets (color/string)
- Full Forge event parity for all 38 events
- Sandboxed script classloader

## Showcase scripts

`ModuleController`, `PlayerEsp`, `SharedBus`, `AttackHook`
