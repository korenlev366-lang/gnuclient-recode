package gnu.client.ui.clickgui;

import gnu.client.module.Category;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure z-order change-detection test for the cached sort in ClickGuiScreen.
 * No Minecraft boot required.
 */
public class CategoryColumnTest {

    @Test
    public void bringToFrontReportsChangeOnlyWhenZChanges() {
        CategoryColumn column = new CategoryColumn(Category.COMBAT);
        int initial = column.getZOrder();

        // Same z → no change (so ClickGUI skips rebuilding its cached z-order list).
        assertFalse(column.bringToFront(initial));
        assertEquals(initial, column.getZOrder());

        // Higher z → change reported.
        assertTrue(column.bringToFront(initial + 5));
        assertEquals(initial + 5, column.getZOrder());

        // Repeated same z → no change again.
        assertFalse(column.bringToFront(initial + 5));
    }
}
