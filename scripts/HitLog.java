Object lastTarget = null;

boolean onPacketSend(Object packet) {
    if (packets.isAttack(packet)) {
        int id = packets.entityId(packet);
        java.util.List<?> entities = world.getEntities();
        for (int i = 0; i < entities.size(); i++) {
            Object e = entities.get(i);
            if (!(e instanceof net.minecraft.entity.Entity)) continue;
            net.minecraft.entity.Entity ent = (net.minecraft.entity.Entity) e;
            if (Mc.entityId(ent) == id) {
                float hp = status.getHealth(e);
                Mc.addChatMessage("\u00a7e[HitLog] Attacked " + ent.getName()
                    + " \u00a7cHP: " + hp + "/" + status.getMaxHealth(e));
                lastTarget = e;
                break;
            }
        }
    }
    return false;
}
