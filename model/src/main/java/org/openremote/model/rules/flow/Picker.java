package org.openremote.model.rules.flow;

public class Picker
{
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public PickerType getType()
    {
        return type;
    }

    public void setType(PickerType type)
    {
        this.type = type;
    }

    public Option[] getOptions()
    {
        return options;
    }

    public void setOptions(Option[] options)
    {
        this.options = options;
    }

    public class Option
    {
        private String name;
        private Object value;

        public Option(String name, Object value)
        {
            this.name = name;
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

        public Object getValue()
        {
            return value;
        }

        public void setValue(Object value)
        {
            this.value = value;
        }
    }

    private String name;
    private PickerType type;
    private Option[] options;

    public Picker(String name, PickerType type, Option[] options)
    {
        this.name = name;
        this.type = type;
        this.options = options;
    }
}
