package org.openremote.manager.rules;

import org.jeasy.rules.api.Rule;
import org.jeasy.rules.core.RuleBuilder;
import org.openremote.manager.rules.flow.NodeStorageService;
import org.openremote.model.rules.flow.Node;
import org.openremote.model.rules.flow.NodeCollection;
import org.openremote.model.rules.flow.NodeExecutionRequestInfo;
import org.openremote.model.rules.flow.NodeType;

import java.util.ArrayList;
import java.util.List;

public class FlowRulesBuilder extends RulesBuilder {
    private List<NodeCollection> nodeCollections = new ArrayList<>();
    private NodeStorageService nodeStorageService;

    public void add(NodeCollection nodeCollection) {
        nodeCollections.add(nodeCollection);
    }

    public Rule[] build() {

        if (nodeStorageService == null)
            throw new NullPointerException("No node storage service set");
        int count = 0;
        List<Rule> rules = new ArrayList<>();
        for (NodeCollection collection : nodeCollections) {
            for (Node node : collection.getNodes()) {
                if (node.getType() != NodeType.OUTPUT) continue;
                try {
                    RulesEngine.RULES_LOG.info("Flow rule created");
                    rules.add(createRule(collection.getName() + " - " + count, collection, node));
                    count++;
                } catch (Exception e) {
                    RulesEngine.RULES_LOG.severe("Flow rule error: " + e.getMessage());
                }
            }
        }
        return rules.toArray(new Rule[0]);
    }

    private Rule createRule(String name, NodeCollection collection, Node outputNode) throws Exception {

        Object implementationResult = nodeStorageService.getImplementationFor(outputNode.getName()).execute(new NodeExecutionRequestInfo(collection, outputNode, null));
        if (!(implementationResult instanceof Action))
            throw new Exception(outputNode.getName() + " node has an invalid implementation");

        //TODO traverse nodes to get condition
        Condition condition = facts -> true;
        Action action = (Action) implementationResult;

        return new RuleBuilder().
                name(name).
                description(collection.getDescription()).
                when(facts -> {
                    Object result;
                    try {
                        result = condition.evaluate((RulesFacts) facts);
                    } catch (Exception ex) {
                        throw new RuntimeException("Error evaluating condition of rule '" + name + "': " + ex.getMessage(), ex);
                    }
                    if (result instanceof Boolean) {
                        return (boolean) result;
                    } else {
                        throw new IllegalArgumentException("Error evaluating condition of rule '" + name + "': result is not boolean but " + result);
                    }
                }).
                then(facts -> {
                    action.execute((RulesFacts) facts);
                }).
                build();
    }

    public NodeStorageService getNodeStorageService() {
        return nodeStorageService;
    }

    public void setNodeStorageService(NodeStorageService nodeStorageService) {
        this.nodeStorageService = nodeStorageService;
    }
}
