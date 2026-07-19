boolean attacked = false;
int wtapTimer = 0;

boolean onPacketSend(Object packet) {
    if (packets.isAttack(packet)) {
        attacked = true;
        wtapTimer = 2;
        client.sendSprintStop();
        client.setSprintKey(false);
    }
    return false;
}

void onPreUpdate() {
    if (wtapTimer > 0) {
        wtapTimer--;
        if (wtapTimer == 0 && attacked) {
            client.sendSprintStart();
            client.setSprintKey(true);
            attacked = false;
        }
    }
    lenience.decayTick();
}
