package org.openremote.manager.rules.flow.definition;

import org.openremote.model.rules.flow.Node;

public class NodePair {
    private Node definition;
    private NodeImplementation implementation;

    public NodePair(Node definition, NodeImplementation implementation) {
        this.definition = definition;
        this.implementation = implementation;
    }

    public NodePair() {
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

