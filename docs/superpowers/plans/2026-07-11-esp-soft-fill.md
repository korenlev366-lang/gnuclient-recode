# ESP Soft Fill-Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace wireframe ESP boxes with one shared soft fill-only draw path across ESP, BedESP, ItemESP, Blink/Lagrange Server ESP, and Backtrack server boxes.

**Architecture:** Add `gnu.client.util.EspDraw` with a shared `DEFAULT_FILL_ALPHA` and a batch-safe `fill(...)` that calls `RenderHelper.drawFilledBox`. Modules keep their existing `RenderHelper.begin()`/`end()` frames and only call `EspDraw.fill`. Remove outline/fill/line-width settings from those modules. Leave `RenderHelper.drawBoundingBox` / `drawLine3D` for non-box effects (e.g. Backtrack trails).

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), LWJGL via `RenderHelper`, JUnit tests under `src/test/java`.

**Spec:** `docs/superpowers/specs/2026-07-11-esp-soft-fill-design.md`

---

## File map

| File | Role |
|------|------|
| Create `src/main/java/gnu/client/util/EspDraw.java` | Shared soft-fill API |
| Create `src/test/java/gnu/client/util/EspDrawTest.java` | Alpha / API smoke tests (no GL) |
| Modify `EspModule.java` | Fill-only; drop `Filled`, `Line Width` |
| Modify `BedEspModule.java` | Fill-only; drop `Filled`; fixed bed height `0.5625` |
| Modify `ItemEspModule.java` | Fill-only; drop outline pass |
| Modify `BlinkModule.java` | Server ESP fill-only; drop `Filled`, `Line Width` |
| Modify `LagrangeModule.java` | Same as Blink |
| Modify `BacktrackModule.java` | Ghost/server box fill-only; remove outline settings; simplify box draw |
| Optional: `docs/mockups/esp-vs-hud-style.html` | Already fill-only on proposed side |

---

### Task 1: `EspDraw` + unit test

**Files:**
- Create: `gnuclient recode/src/main/java/gnu/client/util/EspDraw.java`
- Create: `gnuclient recode/src/test/java/gnu/client/util/EspDrawTest.java`

- [ ] **Step 1: Write the failing test**

```java
package gnu.client.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EspDrawTest {

    @Test
    public void defaultFillAlphaIsSoft() {
        assertTrue(EspDraw.DEFAULT_FILL_ALPHA > 0.05f);
        assertTrue(EspDraw.DEFAULT_FILL_ALPHA < 0.35f);
        assertEquals(0.16f, EspDraw.DEFAULT_FILL_ALPHA, 0.001f);
    }

    @Test
    public void resolveAlphaUsesDefaultWhenNonPositive() {
        assertEquals(EspDraw.DEFAULT_FILL_ALPHA, EspDraw.resolveAlpha(0f), 0.001f);
        assertEquals(EspDraw.DEFAULT_FILL_ALPHA, EspDraw.resolveAlpha(-1f), 0.001f);
        assertEquals(0.25f, EspDraw.resolveAlpha(0.25f), 0.001f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew test --tests 'gnu.client.util.EspDrawTest' -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: FAIL (cannot find `EspDraw` / compilation error).

- [ ] **Step 3: Implement `EspDraw`**

```java
package gnu.client.util;

/**
 * Soft fill-only world ESP boxes. Callers own {@link RenderHelper#begin()}/{@link RenderHelper#end()}.
 * Does not draw outlines.
 */
public final class EspDraw {

    public static final float DEFAULT_FILL_ALPHA = 0.16f;

    private EspDraw() {}

    /** Returns {@code alpha} if &gt; 0, otherwise {@link #DEFAULT_FILL_ALPHA}. */
    public static float resolveAlpha(float alpha) {
        return alpha > 0f ? alpha : DEFAULT_FILL_ALPHA;
    }

    /**
     * Soft fill using {@link #DEFAULT_FILL_ALPHA}.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        fill(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, DEFAULT_FILL_ALPHA);
    }

    /**
     * Soft fill with explicit alpha (&le; 0 falls back to default).
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {
        RenderHelper.drawFilledBox(
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, resolveAlpha(alpha));
    }
}
```

- [ ] **Step 4: Run tests and make sure they pass**

Same command as Step 2. Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 5: Commit** (only if user asked for commits; otherwise skip)

```bash
git add src/main/java/gnu/client/util/EspDraw.java src/test/java/gnu/client/util/EspDrawTest.java
git commit -m "$(cat <<'EOF'
Add EspDraw soft fill-only helper for world ESP boxes.

EOF
)"
```

---

### Task 2: `EspModule` fill-only

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/visual/EspModule.java`

- [ ] **Step 1: Remove outline settings**

Delete fields:

```java
private final BoolSetting filled = ...
private final SliderSetting lineWidth = ...
```

Keep `r`, `g`, `b`, `showSelf`.

- [ ] **Step 2: Replace render body**

In `onRender`, replace the fill/outline loop with:

```java
float fr = r.getValue() / 255.0f;
float fg = g.getValue() / 255.0f;
float fb = b.getValue() / 255.0f;

RenderHelper.begin();
for (EntityData data : cache) {
    double ix = Mc.lerp(data.lastX, data.posX, partialTicks);
    double iy = Mc.lerp(data.lastY, data.posY, partialTicks);
    double iz = Mc.lerp(data.lastZ, data.posZ, partialTicks);
    double rx = ix - rvpX;
    double ry = iy - rvpY;
    double rz = iz - rvpZ;
    double height = data.sneaking ? 1.5 : 1.8;
    EspDraw.fill(
            rx - 0.3, ry, rz - 0.3,
            rx + 0.3, ry + height, rz + 0.3,
            fr, fg, fb);
}
RenderHelper.end();
```

Add `import gnu.client.util.EspDraw;`.

- [ ] **Step 3: Compile**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew compileJava -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: SUCCESS.

- [ ] **Step 4: Commit** (if requested)

```bash
git commit -m "$(cat <<'EOF'
Make player ESP soft fill-only via EspDraw.

EOF
)"
```

---

### Task 3: `BedEspModule` + `ItemEspModule`

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/visual/BedEspModule.java`
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/visual/ItemEspModule.java`

- [ ] **Step 1: BedESP — remove `Filled`, always soft fill**

Delete `filled` setting. Use bed height `0.5625` always (real bed AABB; was the non-filled height).

```java
double height = 0.5625;
RenderHelper.begin();
for (long[] packed : bedCache) {
    // ... unpack bx,by,bz as today ...
    EspDraw.fill(minX, minY, minZ, maxX, maxY, maxZ, fr, fg, fb);
}
RenderHelper.end();
```

- [ ] **Step 2: ItemESP — drop outline pass**

Replace filled+bounding with:

```java
EspDraw.fill(
        rx - 0.15, ry, rz - 0.15,
        rx + 0.15, ry + 0.25, rz + 0.15,
        data.cr, data.cg, data.cb);
```

Keep `RenderHelper.begin()`/`end()` around the loop. Keep `colorForId` unchanged.

- [ ] **Step 3: Compile**

Same `compileJava` command. Expected: SUCCESS.

- [ ] **Step 4: Commit** (if requested)

```bash
git commit -m "$(cat <<'EOF'
Make BedESP and ItemESP soft fill-only.

EOF
)"
```

---

### Task 4: Blink + Lagrange Server ESP

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/network/BlinkModule.java`
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/network/LagrangeModule.java`

**Constraint:** Do not change Blink/Lagrange lag/queue logic — visuals only.

- [ ] **Step 1: Blink — remove `espFilled`, `espLineWidth` and their `visibleWhen`**

Keep `serverEsp` + RGB sliders. Update `visibleWhen` block to only gate RGB (and any remaining ESP settings).

- [ ] **Step 2: Blink — simplify `drawGhostBox`**

```java
private void drawGhostBox(double rx, double ry, double rz,
                          float r, float g, float b) {
    EspDraw.fill(
            rx - 0.3, ry, rz - 0.3,
            rx + 0.3, ry + 1.8, rz + 0.3,
            r, g, b);
}
```

Update `onRender` call site to drop `lw`.

- [ ] **Step 3: Lagrange — same visual cleanup**

Remove `espFilled`, `espLineWidth` + `visibleWhen`. Same `drawGhostBox` body as Blink. Update `onRender` call site. **Do not touch** lag request / queue / timeout code.

- [ ] **Step 4: Compile**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew compileJava -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: SUCCESS.

- [ ] **Step 5: Commit** (if requested)

```bash
git commit -m "$(cat <<'EOF'
Make Blink/Lagrange Server ESP soft fill-only.

EOF
)"
```

---

### Task 5: Backtrack server box fill-only

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/network/BacktrackModule.java`

- [ ] **Step 1: Remove outline-related settings and `visibleWhen`**

Delete fields and all `visibleWhen` lines for:

- `drawOutline`
- `drawFill`
- `outlineColorR`, `outlineColorG`, `outlineColorB`
- `lineWidth`, `outlineWidth`

Keep: `enableVisuals`, `renderServerRecord`, `renderMode`, `drawBox`, `boxColorR/G/B`, trail/pulse/glow/hurt.

- [ ] **Step 2: Simplify render box section**

Replace the glow/fill/box/outline block (the part that calls `drawGhostBox` for box layers) with fill-only logic:

```java
RenderHelper.begin();
if (renderServerRecord.getValue() && enableTrail.getValue())
    drawServerTrail(vp, alpha * 0.6f);

boolean drawBoxLayer = true;
int mode = renderMode.getValue() == null ? 0 : renderMode.getIndex();
// If RENDER_MODES still includes outline-only modes, treat any enabled visuals box mode as fill:
// mode 0 = box, 1 was outline, 2 was both → after this change, mode 1 and 2 also draw soft fill when drawBox is on.
drawBoxLayer = mode == 0 || mode == 1 || mode == 2;

if (enableGlow.getValue()) {
    float gr = glowColorR.getValue() / 255.0f;
    float gg = glowColorG.getValue() / 255.0f;
    float gb = glowColorB.getValue() / 255.0f;
    drawGhostBox(sx - vp[0], sy - vp[1], sz - vp[2],
            gr, gg, gb, alpha * glowIntensity.getValue() * 0.35f);
}

if (drawBox.getValue() && drawBoxLayer)
    drawGhostBox(sx - vp[0], sy - vp[1], sz - vp[2], br, bg, bb, alpha * EspDraw.DEFAULT_FILL_ALPHA);

RenderHelper.end();
```

**Note:** Pulse may already modulate `alpha` — keep existing pulse/hurt color selection above this block unchanged. Prefer multiplying box alpha by `EspDraw.DEFAULT_FILL_ALPHA` **or** pass pulse alpha into `EspDraw.fill(..., alpha)` so pulse still works; use `EspDraw.fill(..., br, bg, bb, alpha * EspDraw.DEFAULT_FILL_ALPHA)` only if pulse alpha is 0–1. Inspect current `alpha` range before choosing — if `alpha` is already 0–1 opacity for the box, call `EspDraw.fill(..., br, bg, bb, alpha * EspDraw.DEFAULT_FILL_ALPHA)` when you want both pulse and soft default; if that looks too faint in-game, use `EspDraw.fill(..., br, bg, bb, Math.max(EspDraw.DEFAULT_FILL_ALPHA * 0.5f, alpha * EspDraw.DEFAULT_FILL_ALPHA))`. Simplest correct approach:

```java
EspDraw.fill(min..., max..., br, bg, bb, EspDraw.resolveAlpha(alpha * EspDraw.DEFAULT_FILL_ALPHA));
```

Actually `resolveAlpha` treats ≤0 as default — for pulse, pass `Math.max(0.01f, alpha) * DEFAULT_FILL_ALPHA` or just `alpha * DEFAULT` when alpha is in 0–1.

**Preferred:**

```java
drawGhostBox(..., br, bg, bb, alpha); // alpha already pulse-modulated 0–1
// inside drawGhostBox:
EspDraw.fill(..., r, g, b, a > 0f ? a * EspDraw.DEFAULT_FILL_ALPHA : EspDraw.DEFAULT_FILL_ALPHA);
```

Wait — that double-scales. Spec: soft default ~0.16. Pulse currently uses alphas like `alpha * 0.25f` for fill. **Do this instead:** `drawGhostBox` calls `EspDraw.fill(..., r, g, b, a)` where `a` is the desired final alpha; at call site pass `alpha * EspDraw.DEFAULT_FILL_ALPHA` for the main box (and glow uses its own scaled alpha). `EspDraw.resolveAlpha` only replaces non-positive.

- [ ] **Step 3: Rewrite `drawGhostBox`**

```java
private void drawGhostBox(double rx, double ry, double rz,
        float r, float g, float b, float alpha) {
    double half = 0.3;
    double height = 1.8;
    if (target != null) {
        half = target.width * 0.5f;
        height = target.height;
    }
    EspDraw.fill(
            rx - half, ry, rz - half,
            rx + half, ry + height, rz + half,
            r, g, b, alpha);
}
```

Remove the old `box/fill/outline/lw` parameters and all `drawBoundingBox` calls.

- [ ] **Step 4: Remove obsolete `renderMode`**

`RENDER_MODES` is currently `Box / Outline / Both`. With outline gone, delete `RENDER_MODES`, the `renderMode` setting, its `visibleWhen`, and any `drawBoxLayer` / mode branching. When `enableVisuals && drawBox`, always soft-fill the ghost box. Keep glow as a separate optional soft fill (still via `drawGhostBox` / `EspDraw.fill`).

- [ ] **Step 5: Compile + settings smoke**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew compileJava test --tests 'gnu.client.util.EspDrawTest' -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: SUCCESS.

Manually confirm (or a tiny test constructing `BacktrackModule` and asserting setting names) that settings list does **not** contain `DrawOutline`, `DrawFill`, `Outline width`, `Line width`.

- [ ] **Step 6: Commit** (if requested)

```bash
git commit -m "$(cat <<'EOF'
Make Backtrack server boxes soft fill-only; drop outline settings.

EOF
)"
```

---

### Task 6: Build jar + in-game checklist

**Files:** none (verification)

- [ ] **Step 1: Full build**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew build -x test -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: `build/libs/gnuclient-1.1.0.jar` updated.

- [ ] **Step 2: In-game checklist**

- ESP / BedESP / ItemESP: soft translucent boxes, no wireframes; no Filled/Line Width in ClickGUI
- Blink + Lagrange Server ESP: same
- Backtrack: soft box when DrawBox on; trails still work; no outline settings
- Non-regression: Blink/Lagrange lag behavior unchanged

- [ ] **Step 3: Done**

Hand jar path to user: `gnuclient recode/build/libs/gnuclient-1.1.0.jar`

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| `EspDraw` + default alpha ~0.16 | Task 1 |
| ESP / Bed / Item fill-only | Tasks 2–3 |
| Blink / Lagrange Server ESP | Task 4 |
| Backtrack boxes + remove outline settings | Task 5 |
| Keep trails / `drawBoundingBox` for non-ESP | Task 5 (trails untouched) |
| Config keys ignored | implicit |
| Build / in-game verify | Task 6 |

No TBD placeholders. API names consistent: `EspDraw.fill`, `DEFAULT_FILL_ALPHA`, `resolveAlpha`.
