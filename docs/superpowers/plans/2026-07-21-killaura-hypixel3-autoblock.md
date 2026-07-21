# HYPIXEL3 Autoblock Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port wsamiaw HYPIXEL3 KillAura autoblock + Disable Keep Sprint On KB into gnuclient.

**Architecture:** Extend `KillAuraAutoBlock` switch; wire mode/setting/KB packet hook in `KillAuraModule`.

**Tech Stack:** Java 8, Forge 1.8.9, existing AUTO_BLOCK blink via BlinkManager.

**Spec:** `docs/superpowers/specs/2026-07-21-killaura-hypixel3-autoblock-design.md`

---

### Task 1: KillAuraAutoBlock HYPIXEL3 case

**Files:** `.../killaura/KillAuraAutoBlock.java`

- [ ] Add `public static final int HYPIXEL3 = 10;`
- [ ] Add `private int hypixel3Asw;`
- [ ] Reset `hypixel3Asw` in `reset()`
- [ ] Add `case HYPIXEL3:` mirroring wsamiaw (blink on, 0/1 stop+no attack, 2 swap+blocked pulse)

### Task 2: KillAuraModule wiring

**Files:** `.../combat/KillAuraModule.java`

- [ ] Append `"HYPIXEL3"` to `AUTO_BLOCK_MODES`
- [ ] Add `disableKeepSprintOnKb` BoolSetting (default true), `visibleWhen` HYPIXEL3
- [ ] Ensure KA is PacketListener (or add); onReceive self S12 / non-zero S27 → `setSprinting(false)` when mode HYPIXEL3 && setting

### Task 3: Build

- [ ] `./gradlew build`
