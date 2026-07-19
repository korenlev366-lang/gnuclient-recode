void onPreUpdate() {
    if (lenience.getKbWindow() > 0) {
        client.setSprintKey(false);
        client.setSprinting(false);
    }
}
