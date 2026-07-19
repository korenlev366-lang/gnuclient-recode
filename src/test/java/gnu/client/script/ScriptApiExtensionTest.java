package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit coverage for script API helpers used by the example scripts. */
public class ScriptApiExtensionTest {

    @Test
    public void inventoryToContainerSlotMapsHotbarMainAndArmor() {
        assertEquals(36, Mc.inventoryToContainerSlot(0));
        assertEquals(44, Mc.inventoryToContainerSlot(8));
        assertEquals(9, Mc.inventoryToContainerSlot(9));
        assertEquals(35, Mc.inventoryToContainerSlot(35));
        assertEquals(8, Mc.inventoryToContainerSlot(36)); // boots
        assertEquals(5, Mc.inventoryToContainerSlot(39)); // helmet
        assertEquals(-1, Mc.inventoryToContainerSlot(40));
    }

    @Test
    public void armorContainerSlotMatchesArmorType() {
        assertEquals(5, Mc.armorContainerSlot(0));
        assertEquals(8, Mc.armorContainerSlot(3));
        assertEquals(-1, Mc.armorContainerSlot(4));
    }

    @Test
    public void chatTextReadsOutboundAndInbound() {
        assertEquals("hello", PacketHelper.chatMessage(new C01PacketChatMessage("hello")));
        S02PacketChat inbound = new S02PacketChat(new ChatComponentText("Winner!"));
        assertTrue(PacketHelper.isChatReceive(inbound));
        assertEquals("Winner!", PacketHelper.chatMessage(inbound));
        assertFalse(PacketHelper.isChatReceive(new C01PacketChatMessage("x")));
    }

    @Test
    public void keepAliveIdRoundTripOnC00() {
        C00PacketKeepAlive packet = new C00PacketKeepAlive(42);
        assertEquals(42, PacketHelper.keepAliveId(packet));
        PacketHelper.keepAliveSetId(packet, 99);
        assertEquals(99, PacketHelper.keepAliveId(packet));
        assertEquals(99, packetsFacade().keepAliveId(packet));
    }

    @Test
    public void posLookRotationSettersUpdatePacket() {
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(
                1, 2, 3, 10f, 20f, Collections.emptySet());
        PacketHelper.posLookSetRotation(packet, 90f, -45f);
        assertEquals(90f, PacketHelper.posLookYaw(packet), 0.001f);
        assertEquals(-45f, PacketHelper.posLookPitch(packet), 0.001f);
    }

    @Test
    public void transactionUidRoundTripOnC0F() {
        C0FPacketConfirmTransaction packet = new C0FPacketConfirmTransaction(0, (short) 7, true);
        assertEquals(7, PacketHelper.transactionUid(packet));
        PacketHelper.transactionSetUid(packet, (short) 11);
        assertEquals(11, PacketHelper.transactionUid(packet));
    }

    @Test
    public void packetsFacadeExposesChatAndSprintHelpers() {
        Packets packets = packetsFacade();
        assertTrue(packets.isChat(new C01PacketChatMessage("a")));
        assertEquals("a", packets.chatText(new C01PacketChatMessage("a")));
    }

    private static Packets packetsFacade() {
        return Packets.INSTANCE;
    }
}
