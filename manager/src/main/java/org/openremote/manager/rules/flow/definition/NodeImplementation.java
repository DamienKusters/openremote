package org.openremote.manager.rules.flow.definition;

import org.openremote.manager.rules.flow.NodeExecutionRequestInfo;

public interface NodeImplementation {
    Object execute(NodeExecutionRequestInfo info);
}
