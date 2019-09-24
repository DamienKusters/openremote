package org.openremote.manager.rules;

import org.jeasy.rules.api.Rule;
import org.jeasy.rules.core.RuleBuilder;
import org.openremote.model.rules.flow.NodeCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class FlowRulesBuilder extends RulesBuilder
{
    private List<NodeCollection> nodeCollections = new ArrayList<>();

    public void add(NodeCollection nodeCollection)
    {
        nodeCollections.add(nodeCollection);
    }

    public Rule[] build()
    {
        List<Rule> rules = new ArrayList<>();
        for (NodeCollection collection : nodeCollections)
        {
            org.jeasy.rules.api.Condition condition = facts -> {
                // traverse structure to find condition
                return false;
            };

            org.jeasy.rules.api.Action action = facts -> {
                // traverse structure to find action
            };

            Rule rule = new RuleBuilder().
                    name(collection.getName()).
                    description(collection.getDescription()).
                    when(condition).
                    then(action).
                    build();

            RulesEngine.RULES_LOG.log(Level.WARNING, rule.getName() + " rule registered! hooray!");
            rules.add(rule);
        }
        return rules.toArray(new Rule[0]);
    }
}
