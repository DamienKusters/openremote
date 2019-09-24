package org.openremote.model.rules.flow;

public class ServerReadyConnection
{
    private ServerReadySocket from;
    private ServerReadySocket to;

    public ServerReadyConnection(ServerReadySocket from, ServerReadySocket to)
    {
        this.from = from;
        this.to = to;
    }

    public ServerReadyConnection()
    {
        from = null;
        to = null;
    }

    public ServerReadySocket getFrom()
    {
        return from;
    }

    public void setFrom(ServerReadySocket from)
    {
        this.from = from;
    }

    public ServerReadySocket getTo()
    {
        return to;
    }

    public void setTo(ServerReadySocket to)
    {
        this.to = to;
    }
}
