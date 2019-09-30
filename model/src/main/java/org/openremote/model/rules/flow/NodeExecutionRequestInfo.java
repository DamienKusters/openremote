package org.openremote.model.rules.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NodeExecutionRequestInfo {
    private NodeCollection collection;

    private int outputSocketIndex;
    private NodeSocket outputSocket;

    private Node node;
    private NodeSocket[] inputs;
    private NodeSocket[] outputs;
    private NodeInternal[] internals;

    public NodeExecutionRequestInfo() {
        collection = new NodeCollection();
        outputSocketIndex = -1;
        outputSocket = null;
        node = null;
        inputs = new NodeSocket[]{};
        outputs = new NodeSocket[]{};
        internals = new NodeInternal[]{};
    }

    public NodeExecutionRequestInfo(NodeCollection collection, int outputSocketIndex, NodeSocket outputSocket, Node node, NodeSocket[] inputs, NodeSocket[] outputs, NodeInternal[] internals) {
        this.collection = collection;
        this.outputSocketIndex = outputSocketIndex;
        this.outputSocket = outputSocket;
        this.node = node;
        this.inputs = inputs;
        this.outputs = outputs;
        this.internals = internals;
    }

    public NodeExecutionRequestInfo(NodeCollection collection, Node node, NodeSocket socket) {
        if (socket != null && Arrays.stream(node.getOutputs()).noneMatch(c -> c.getNodeId().equals(node.getId())))
            throw new IllegalArgumentException("Given socket does not belong to given node");

        this.collection = collection;
        this.outputSocketIndex = Arrays.asList(node.getOutputs()).indexOf(socket);
        this.outputSocket = socket;
        this.node = node;

        List<NodeSocket> inputs = new ArrayList<>();
        for (NodeSocket s : node.getInputs()) {
            inputs.addAll(Arrays.stream(collection.getConnections()).filter(c -> c.getTo().equals(s)).map(NodeConnection::getFrom).collect(Collectors.toList()));
        }
        this.inputs = inputs.toArray(new NodeSocket[0]);

        List<NodeSocket> outputs = new ArrayList<>();
        for (NodeSocket s : node.getOutputs()) {
            outputs.addAll(Arrays.stream(collection.getConnections()).filter(c -> c.getFrom().equals(s)).map(NodeConnection::getTo).collect(Collectors.toList()));
        }
        this.outputs = outputs.toArray(new NodeSocket[0]);

        this.internals = node.getInternals();
    }

    public NodeCollection getCollection() {
        return collection;
    }

    public void setCollection(NodeCollection collection) {
        this.collection = collection;
    }

    public int getOutputSocketIndex() {
        return outputSocketIndex;
    }

    public void setOutputSocketIndex(int outputSocketIndex) {
        this.outputSocketIndex = outputSocketIndex;
    }

    public NodeSocket getOutputSocket() {
        return outputSocket;
    }

    public void setOutputSocket(NodeSocket outputSocket) {
        this.outputSocket = outputSocket;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
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

    public NodeInternal[] getInternals() {
        return internals;
    }

    public void setInternals(NodeInternal[] internals) {
        this.internals = internals;
    }
}
