package org.openremote.model.rules.flow.definition;

import org.openremote.model.rules.flow.NodeExecutionRequestInfo;
import org.openremote.model.value.Value;

public interface NodeImplementation {
    Value execute(NodeExecutionRequestInfo info);
}
