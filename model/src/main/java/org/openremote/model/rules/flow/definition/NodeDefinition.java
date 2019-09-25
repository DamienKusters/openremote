package org.openremote.model.rules.flow.definition;

import org.openremote.model.rules.flow.Node;

public class NodeDefinition {
    private Node definition;
    private NodeImplementation implementation;

    public NodeDefinition(Node definition, NodeImplementation implementation) {
        this.definition = definition;
        this.implementation = implementation;
    }

    public NodeDefinition() {
        this.definition = null;
        this.implementation = null;
    }

    public Node getDefinition() {
        return definition;
    }

    public void setDefinition(Node definition) {
        this.definition = definition;
    }

    public NodeImplementation getImplementation() {
        return implementation;
    }

    public void setImplementation(NodeImplementation implementation) {
        this.implementation = implementation;
    }
}

