package gnu.client.script;

import gnu.client.ui.hud.HudRenderer;
import gnu.client.ui.hud.NotificationQueue;

/** Script-facing HUD helpers (toasts / notifications). */
public final class Hud {

    public static final Hud INSTANCE = new Hud();

    private Hud() {}

    /** Show a toast notification (same style as module enable/disable toasts). */
    public void notify(String title) {
        notify(title, true);
    }

    public void notify(String title, boolean positive) {
        if (title == null || title.isEmpty())
            return;
        NotificationQueue queue = HudRenderer.instance().getNotificationQueue();
        if (queue != null)
            queue.pushToast(title, positive);
    }
}
