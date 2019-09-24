package org.openremote.model.rules.flow;

public class ServerReadyNodeCollection
{
    private String name;
    private String description;
    private ServerReadyNode[] nodes;
    private ServerReadyConnection[] connections;

    public ServerReadyNodeCollection(String name, String description, ServerReadyNode[] nodes, ServerReadyConnection[] connections)
    {
        this.name = name;
        this.description = description;
        this.nodes = nodes;
        this.connections = connections;
    }

    public ServerReadyNodeCollection()
    {
        name = "Unnamed node collection";
        description = "No description provided";
        nodes = new ServerReadyNode[]{};
        connections = new ServerReadyConnection[]{};
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public ServerReadyNode[] getNodes()
    {
        return nodes;
    }

    public void setNodes(ServerReadyNode[] nodes)
    {
        this.nodes = nodes;
    }

    public ServerReadyConnection[] getConnections()
    {
        return connections;
    }

    public void setConnections(ServerReadyConnection[] connections)
    {
        this.connections = connections;
    }
}

