package gnu.client.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pure-state pixel/scissor math tests — no Minecraft boot, no GL calls.
 */
public class UiKitPixelTest {

    @Test
    public void snapRoundsToPhysicalPixels() {
        assertEquals(1.0f, UiKit.PixelAlign.snap(1.2f, 2f), 0.0001f);
        assertEquals(1.5f, UiKit.PixelAlign.snap(1.4f, 2f), 0.0001f);
        assertEquals(2.0f / 3f, UiKit.PixelAlign.snap(0.7f, 3f), 0.0001f);
        assertEquals(1.0f, UiKit.PixelAlign.snap(0.9f, 3f), 0.0001f);
    }

    @Test
    public void toFramebufferRectInvertsYAndClamps() {
        // scale=2, display 200x100: GUI (10,10,20,15) → left=20 top=20 right=60 bottom=50
        // fbY = 100 - 50 = 50, height = 30
        UiKit.FbRect r = UiKit.PixelAlign.toFramebufferRect(10f, 10f, 20f, 15f, 2f, 200, 100);
        assertEquals(20, r.x);
        assertEquals(50, r.y);
        assertEquals(40, r.width);
        assertEquals(30, r.height);

        // Clamp: rect extending past top-left of framebuffer
        UiKit.FbRect clamped = UiKit.PixelAlign.toFramebufferRect(-5f, -5f, 10f, 10f, 1f, 50, 50);
        assertTrue(clamped.x >= 0);
        assertTrue(clamped.y >= 0);
        assertTrue(clamped.x + clamped.width <= 50);
        assertTrue(clamped.y + clamped.height <= 50);
    }

    @Test
    public void nestedScissorIntersectsWithoutGl() {
        UiKit.FbRect a = new UiKit.FbRect(10, 10, 100, 80);
        UiKit.FbRect b = new UiKit.FbRect(40, 30, 100, 100);
        UiKit.FbRect i = UiKit.ScissorStack.intersect(a, b);
        assertEquals(40, i.x);
        assertEquals(30, i.y);
        assertEquals(70, i.width);
        assertEquals(60, i.height);

        UiKit.ScissorStack stack = new UiKit.ScissorStack();
        stack.setApplyGl(false);
        stack.push(10, 10, 100, 80);
        stack.push(40, 30, 100, 100);
        UiKit.FbRect cur = stack.current();
        assertEquals(40, cur.x);
        assertEquals(30, cur.y);
        assertEquals(70, cur.width);
        assertEquals(60, cur.height);
        stack.pop();
        assertEquals(10, stack.current().x);
        assertEquals(100, stack.current().width);
        stack.pop();
        assertEquals(0, stack.depth());
    }
}
