package org.openremote.model.rules.flow;

public class Node {
    // Generation of ID is the responsibility of the npm package
    private String id;
    private NodeType type;
    private String name;
    private NodePosition position;
    private NodeInternal[] internals;
    private NodeSocket[] inputs;
    private NodeSocket[] outputs;
    private String displayCharacter;

    public Node(NodeType type, String name, NodeInternal[] internals, NodeSocket[] inputs, NodeSocket[] outputs) {
        this.id = "INVALID ID";
        this.position = new NodePosition(0, 0);

        this.type = type;
        this.name = name;
        this.position = new NodePosition(0, 0);
        this.internals = internals;
        this.inputs = inputs;
        this.outputs = outputs;
        this.displayCharacter = null;
    }

    public Node(NodeType type, String displayCharacter,  String name, NodeInternal[] internals, NodeSocket[] inputs, NodeSocket[] outputs) {
        if (internals.length != 0)
            throw new IllegalArgumentException("A node cannot have internals when a display character is specified");

        this.id = "INVALID ID";
        this.position = new NodePosition(0, 0);

        this.type = type;
        this.name = name;
        this.position = new NodePosition(0, 0);
        this.internals = internals;
        this.inputs = inputs;
        this.outputs = outputs;
        this.displayCharacter = displayCharacter;
    }

    public Node() {
        id = "INVALID ID";
        type = NodeType.INPUT;
        name = "Unnamed node";
        position = new NodePosition();
        internals = new NodeInternal[]{};
        inputs = new NodeSocket[]{};
        outputs = new NodeSocket[]{};
        displayCharacter = null;
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NodePosition getPosition() {
        return position;
    }

    public void setPosition(NodePosition position) {
        this.position = position;
    }

    public NodeInternal[] getInternals() {
        return internals;
    }

    public void setInternals(NodeInternal[] internals) {
        this.internals = internals;
    }

    public NodeSocket[] getInputs() {
        return inputs;
    }

    public void setInputs(NodeSocket[] inputs) {
        this.inputs = inputs;
    }

    public NodeSocket[] getOutputs() {
        return outputs;
    }

    public void setOutputs(NodeSocket[] outputs) {
        this.outputs = outputs;
    }

    public String getDisplayCharacter() {
        return displayCharacter;
    }

    public void setDisplayCharacter(String displayCharacter) {
        this.displayCharacter = displayCharacter;
    }
}

