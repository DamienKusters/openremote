package org.openremote.model.rules.flow.definition;

import org.openremote.model.rules.flow.NodeExecutionRequestInfo;

public interface NodeImplementation {
    Object execute(NodeExecutionRequestInfo info);
}
