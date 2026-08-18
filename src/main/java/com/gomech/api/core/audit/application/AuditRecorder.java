package com.gomech.api.core.audit.application;

import com.gomech.api.core.audit.api.AuditRecordRequest;
import com.gomech.api.core.audit.domain.AuditEntry;
import com.gomech.api.core.authorization.api.ActorContext;

public interface AuditRecorder {

    AuditEntry record(ActorContext actor, AuditRecordRequest request);
}
