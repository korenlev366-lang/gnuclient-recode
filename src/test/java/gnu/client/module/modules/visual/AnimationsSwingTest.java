package gnu.client.module.modules.visual;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AnimationsSwingTest {
    @Test
    public void swingSpeedZeroIsVanillaSix() {
        assertEquals(6, AnimationsModule.armSwingAnimationEnd(0));
    }

    @Test
    public void swingSpeedHundredIsTwenty() {
        assertEquals(20, AnimationsModule.armSwingAnimationEnd(100));
    }

    @Test
    public void swingSpeedClampsBelowZero() {
        assertEquals(6, AnimationsModule.armSwingAnimationEnd(-10));
    }

    @Test
    public void swingSpeedClampsAboveHundred() {
        assertEquals(20, AnimationsModule.armSwingAnimationEnd(200));
    }
}
