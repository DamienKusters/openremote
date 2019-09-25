package org.openremote.model.rules.flow;

public class NodeSocket {
    private String id;
    private String name;
    private String type;
    private String nodeId;
    private int index;

    public NodeSocket(String id, String name, String type, String nodeId, int index) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.nodeId = nodeId;
        this.index = index;
    }

    public NodeSocket() {
        id = "INVALID ID";
        name = "Unnamed socket";
        type = "INVALID TYPE";
        nodeId = "INVALID NODE ID";
        index = -1;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
