package org.openremote.model.rules.flow;

public class ServerReadyInternal
{
    private String name;
    private Picker picker;
    private Object value;

    public ServerReadyInternal(String name, Picker picker, Object value)
    {
        this.name = name;
        this.picker = picker;
        this.value = value;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Picker getPicker()
    {
        return picker;
    }

    public void setPicker(Picker picker)
    {
        this.picker = picker;
    }

    public Object getValue()
    {
        return value;
    }

    public void setValue(Object value)
    {
        this.value = value;
    }
}
