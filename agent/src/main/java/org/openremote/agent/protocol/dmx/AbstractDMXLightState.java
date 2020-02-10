package org.openremote.agent.protocol.dmx;

public class AbstractDMXLightState {

    private int lightId;

    public AbstractDMXLightState(int lightId) {
        this.lightId = lightId;
    }

    public int getLightId() {
        return this.lightId;
    }

}
