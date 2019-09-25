package org.openremote.model.rules.flow;

public class NodeConnection {
    private NodeSocket from;
    private NodeSocket to;

    public NodeConnection(NodeSocket from, NodeSocket to) {
        this.from = from;
        this.to = to;
    }

    public NodeConnection() {
        from = null;
        to = null;
    }

    public NodeSocket getFrom() {
        return from;
    }

    public void setFrom(NodeSocket from) {
        this.from = from;
    }

    public NodeSocket getTo() {
        return to;
    }

    public void setTo(NodeSocket to) {
        this.to = to;
    }
}
