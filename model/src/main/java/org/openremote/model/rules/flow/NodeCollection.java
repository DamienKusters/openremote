package org.openremote.model.rules.flow;

public class NodeCollection
{
    private String name;
    private String description;
    private GraphNode[] nodes;
    private NodeConnection[] connections;

    public NodeCollection(String name, String description, GraphNode[] nodes, NodeConnection[] connections)
    {
        this.name = name;
        this.description = description;
        this.nodes = nodes;
        this.connections = connections;
    }

    public NodeCollection()
    {
        name = "Unnamed node collection";
        description = "No description provided";
        nodes = new GraphNode[]{};
        connections = new NodeConnection[]{};
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

    public GraphNode[] getNodes()
    {
        return nodes;
    }

    public void setNodes(GraphNode[] nodes)
    {
        this.nodes = nodes;
    }

    public NodeConnection[] getConnections()
    {
        return connections;
    }

    public void setConnections(NodeConnection[] connections)
    {
        this.connections = connections;
    }
}

