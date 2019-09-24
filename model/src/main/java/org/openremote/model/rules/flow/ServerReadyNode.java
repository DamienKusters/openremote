package org.openremote.model.rules.flow;

public class ServerReadyNode
{
    private String id;
    private String type;
    private String name;
    private NodePosition position;
    private ServerReadyInternal[] internals;
    private ServerReadySocket[] inputs;
    private ServerReadySocket[] outputs;

    public ServerReadyNode(String id, String type, String name, NodePosition position, ServerReadyInternal[] internals, ServerReadySocket[] inputs, ServerReadySocket[] outputs)
    {
        this.id = id;
        this.type = type;
        this.name = name;
        this.position = position;
        this.internals = internals;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public ServerReadyNode()
    {
        id = "INVALID ID";
        type = "INVALID NODE TYPE";
        name = "Unnamed node";
        position = new NodePosition();
        internals = new ServerReadyInternal[]{};
        inputs = new ServerReadySocket[]{};
        outputs = new ServerReadySocket[]{};
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public NodePosition getPosition()
    {
        return position;
    }

    public void setPosition(NodePosition position)
    {
        this.position = position;
    }

    public ServerReadyInternal[] getInternals()
    {
        return internals;
    }

    public void setInternals(ServerReadyInternal[] internals)
    {
        this.internals = internals;
    }

    public ServerReadySocket[] getInputs()
    {
        return inputs;
    }

    public void setInputs(ServerReadySocket[] inputs)
    {
        this.inputs = inputs;
    }

    public ServerReadySocket[] getOutputs()
    {
        return outputs;
    }

    public void setOutputs(ServerReadySocket[] outputs)
    {
        this.outputs = outputs;
    }
}

