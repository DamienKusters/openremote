import {customElement, html, LitElement, TemplateResult, property} from "lit-element";
import {LogicGroup, JsonRule, RuleCondition, AssetDescriptor, LogicGroupOperator} from "@openremote/model";
import {OrRuleChangedEvent, RulesConfig} from "./index";
import {whenStyle} from "./style";
import "./or-rule-condition";
import i18next from "i18next";

@customElement("or-rule-when")
class OrRuleWhen extends LitElement {

    static get styles() {
        return whenStyle;
    }

    @property({type: Object})
    public rule?: JsonRule;

    public readonly?: boolean;

    public config?: RulesConfig;

    public assetDescriptors?: AssetDescriptor[];

    protected ruleGroupTemplate(group: LogicGroup<RuleCondition>, parentGroup?: LogicGroup<RuleCondition>): TemplateResult | undefined {
        if (!group) {
            return html``;
        }

        const hasItems = (group.items && group.items.length > 0) || (group.groups && group.groups.length > 0);
        const showGroupOutline = !this.config || !this.config.controls || this.config.controls.hideWhenGroupOutline !== true;
        const showAddCondition = !this.readonly && (!this.config || !this.config.controls || this.config.controls.hideWhenAddCondition !== true);
        const showRemoveCondition = !this.readonly && group.items && group.items.length > 1;
        const showAddGroup = !this.readonly && (!this.config || !this.config.controls || this.config.controls.hideWhenAddGroup !== true);
        const showRemoveGroup = !this.readonly;

        return html`
            <div class="rule-group ${showGroupOutline ? "visible" : "hidden"}">
                ${showRemoveGroup ? html`
                    <button class="button-clear remove-button" @click="${() => this.removeGroup(group)}">
                        <div></div>
                        <or-icon icon="close-circle"></or-icon>                        
                    </button>
                ` : ``} 
                               
                <div class="rule-group-items">
                    ${group.items && group.items.length > 0 ? group.items.map((condition: RuleCondition) => {
                        return html `
                            <div class="rule-group-item">
                                <button @click="${() => this.toggleGroup(group)}" class="button-clear operator"><span>${i18next.t(!group.operator || group.operator === LogicGroupOperator.AND ? "and" : "or")}</span></button>
                                <div class="rule-condition">
                                    <or-rule-condition .config="${this.config}" .assetDescriptors="${this.assetDescriptors}" .ruleCondition="${condition}" .readonly="${this.readonly}" ></or-rule-condition>
                                    ${showRemoveCondition ? html`
                                        <button class="button-clear" @click="${() => this.removeCondition(condition)}"><or-icon icon="close-circle"></or-icon></input>
                                    ` : ``}
                                </div>
                            </div>
                        `;
                    }) : ``}
                    
                    ${group.groups && group.groups.length > 0 ? html`
                        ${group.groups.map((childGroup: LogicGroup<RuleCondition>) => html`
                            <div class="rule-group-item">
                                <button @click="${() => this.toggleGroup(group)}" class="button-clear operator"><span>${i18next.t(!group.operator || group.operator === LogicGroupOperator.AND ? "and" : "or")}</span></button>
                                ${this.ruleGroupTemplate(childGroup, group)}
                            </div>
                        `)}
                    ` : ``}
                </div>
                
                ${showAddCondition || showAddGroup ? html`
                    <div class="add-buttons-container ${hasItems ? "" : "hidden"}">
                        ${showAddCondition ? html`<button class="button-clear add-button" @click="${() => this.addCondition(group)}"><or-icon icon="plus-circle"></or-icon><or-translate value="rulesEditorAddCondition"></or-translate></button>` : ``}
                        ${showAddGroup ? html`<button class="button-clear add-button" @click="${() => this.addGroup(group)}"><or-icon icon="plus-circle"></or-icon><or-translate value="rulesEditorAddGroup"></or-translate></button>` : ``}
                    </div>
                ` : ``}
            </div>
        `;
    }

    protected dateTimePredicateTemplate() {
        return html`<span>DATE TIME PREDICATE NOT IMPLEMENTED</span>`;
    }

    protected render() {
        if (!this.rule) {
            return html``;
        }

        if (!this.rule.when) {
            this.rule.when = {};
        }

        return this.ruleGroupTemplate(this.rule.when);
    }

    private toggleGroup(group: LogicGroup<RuleCondition>) {
        if (group.operator === LogicGroupOperator.OR) {
            group.operator = LogicGroupOperator.AND;
        } else {
            group.operator = LogicGroupOperator.OR;
        }
        this.dispatchEvent(new OrRuleChangedEvent());
        this.requestUpdate();
    }

    private addGroup(parent: JsonRule | LogicGroup<RuleCondition>) {
        if (!parent) {
            return;
        }

        let newGroup: LogicGroup<RuleCondition> = {};

        if (this.config && this.config.templates && this.config.templates.whenGroup) {
            newGroup = JSON.parse(JSON.stringify(this.config.templates.whenGroup)) as LogicGroup<RuleCondition>;
        }

        if ((parent as JsonRule).name !== undefined) {
            const rule = parent as JsonRule;
            rule.when = newGroup;
        } else {
            const condition = parent as LogicGroup<RuleCondition>;
            if (!condition.groups) {
                condition.groups = [];
            }
            condition.groups.push(newGroup);
        }

        this.dispatchEvent(new OrRuleChangedEvent());
        this.requestUpdate();
    }

    private removeGroup(group: LogicGroup<RuleCondition>) {
        if (!this.rule || !this.rule.when || !group) {
            return;
        }

        if (this.rule.when === group) {
            this.rule.when = undefined;
            this.dispatchEvent(new OrRuleChangedEvent());
            this.requestUpdate();
            return;
        }

        const parent = this.getGroupParent(group, this.rule.when);
        if (parent) {
            const index = parent.groups!.indexOf(group);
            parent.groups!.splice(index, 1);
            this.dispatchEvent(new OrRuleChangedEvent());
            this.requestUpdate();
        }
    }

    private addCondition(parent: LogicGroup<RuleCondition>) {
        if (!parent) {
            return;
        }

        if (!parent.items) {
            parent.items = [];
        }

        let newTrigger: RuleCondition = {};

        if (this.config && this.config.templates && this.config.templates.whenCondition) {
            newTrigger = JSON.parse(JSON.stringify(this.config.templates.whenCondition)) as RuleCondition;
        }

        parent.items.push(newTrigger);
        this.dispatchEvent(new OrRuleChangedEvent());
        this.requestUpdate();
    }

    private removeCondition(condition: RuleCondition) {
        if (!this.rule || !this.rule.when || !condition) {
            return;
        }

        const parent = this.getConditionParent(condition, this.rule.when);

        if (parent) {
            const index = parent.items!.indexOf(condition);
            parent.items!.splice(index, 1);
            this.dispatchEvent(new OrRuleChangedEvent());
            this.requestUpdate();
        }
    }

    private getConditionParent(condition: RuleCondition, start: LogicGroup<RuleCondition>) {
        if (!condition || !start) {
            return;
        }

        if (start.items) {
            const index = start.items.indexOf(condition);
            if (index >= 0) {
                return start;
            }
        }

        if (start.groups) {
            for (const group of start.groups) {
                const result = this.getConditionParent(condition, group);
                if (result) {
                    return group;
                }
            }
        }
    }

    private getGroupParent(group: LogicGroup<RuleCondition>, start: LogicGroup<RuleCondition>) {
        if (!group || !start || !start.groups) {
            return;
        }

        const index = start.groups.indexOf(group);
        if (index >= 0) {
            return start;
        }

        for (const childGroup of start.groups) {
            const result = this.getGroupParent(group, childGroup);
            if (result) {
                return childGroup;
            }
        }
    }

    private deleteWhenCondition(e: any) {

        const index = e.detail.index;

        if (this.rule && this.rule.when && this.rule.when.items && this.rule.when.items.length > 0 && this.rule.when.items[0].assets && this.rule.when.items[0].assets.attributes && this.rule.when.items[0].assets.attributes.items) {
            this.rule.when.items[0].assets.attributes.items.splice(index, 1);
            this.dispatchEvent(new OrRuleChangedEvent());
            this.requestUpdate();
        }
    }
}
