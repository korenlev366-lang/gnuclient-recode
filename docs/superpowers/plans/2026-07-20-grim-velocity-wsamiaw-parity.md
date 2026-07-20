# Grim Velocity wsamiaw Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace GNUClient dig-cancel `Grim` with wsamiaw adaptive GRIM (scale S12, setback, optional C0F delay).

**Architecture:** Rewrite `GrimVelocity` as the mode strategy; add Grim-gated settings on `VelocityModule`. Scale via `IAccessorS12PacketEntityVelocity`. No new mixins.

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), existing PacketListener / VelocityMode hooks.

**Spec:** `docs/superpowers/specs/2026-07-20-grim-velocity-wsamiaw-parity-design.md`

---

## File map

| File | Role |
|------|------|
| `.../VelocityModule.java` | Grim settings + Chance visibility |
| `.../velocity/GrimVelocity.java` | Full rewrite of mode logic |

---

### Task 1: Add Grim settings to VelocityModule

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/VelocityModule.java`

- [x] **Step 1:** After `grimReduceJumpLimit`, add Grim settings (done)
- [x] **Step 2:** Visibility for Chance + Grim settings (done)
---

### Task 2: Rewrite GrimVelocity

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/velocity/GrimVelocity.java`

- [ ] **Step 1:** Replace dig-cancel implementation with wsamiaw parity:
  - State: currentH/V, adaptTimer, setback, txTimer, pendingSetbackCheck, delayed C0F deque
  - `onEnable` / `onDisable`: reset + flush
  - `onUpdate(pre)`: adapt step + tx countdown/flush
  - `onReceive`: S19 allowNext; S08 setback; S12 scale (never cancel)
  - `onSend`: hold C0F while txTimer > 0
  - Chance + Fake Check gates per spec
  - Flush via `Mc.addToSendQueue` (not no-event)

---

### Task 3: Verify build

- [ ] **Step 1:** `cd "gnuclient recode" && ./gradlew build`
- [ ] **Step 2:** Confirm compile success; note jar path

---

## Done when

- Dig-cancel gone from `Grim`
- Settings match spec defaults
- `GrimReduce` unchanged
- Build passes
