package org.openremote.model.rules.flow;

public class Picker {
    private String name;
    private PickerType type;
    private Option[] options;

    public Picker(String name, PickerType type, Option[] options) {
        this.name = name;
        this.type = type;
        this.options = options;
    }

    public Picker(String name, PickerType type) {
        this.name = name;
        this.type = type;
        this.options = new Option[0];
    }

    public Picker() {
        name = "Unnamed picker";
        type = PickerType.NUMBER;
        options = new Option[]{};
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PickerType getType() {
        return type;
    }

    public void setType(PickerType type) {
        this.type = type;
    }

    public Option[] getOptions() {
        return options;
    }

    public void setOptions(Option[] options) {
        this.options = options;
    }
}
