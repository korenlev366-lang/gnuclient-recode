package gnu.client.command;

import gnu.client.common.GnuLog;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;

/**
 * Intercepts outbound chat packets for client-only dot-commands (e.g. {@code .bind}).
 */
public final class ChatCommandHandler implements PacketListener {

    public static final ChatCommandHandler INSTANCE = new ChatCommandHandler();

    private static final int SEND_PRIORITY = 500;

    private ChatCommandHandler() {}

    public static void register() {
        PacketEvents.register(INSTANCE);
    }

    @Override
    public int sendPriority() {
        return SEND_PRIORITY;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!PacketHelper.isChatSend(packet))
            return false;
        String message = PacketHelper.chatMessage(packet);
        if (message == null)
            return false;

        if (ConfigCommand.handles(message))
            return addChat(ConfigCommand.execute(message));
        if (HelpCommand.handles(message))
            return addChat(HelpCommand.execute(message));
        if (!BindCommand.handles(message))
            return false;

        return addChat(BindCommand.execute(message));
    }

    private static boolean addChat(String result) {
        if (result != null) {
            GnuLog.log("CMD_ " + result);
            Mc.addChatMessage("§7[GNU] §f" + result);
        }
        return result != null;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }
}
