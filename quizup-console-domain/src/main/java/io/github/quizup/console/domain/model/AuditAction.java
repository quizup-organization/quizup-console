package io.github.quizup.console.domain.model;

/**
 * Action d'exploitation auditée.
 */
public enum AuditAction {
    RETRY_DLQ_SEQUENCE,
    DELETE_DLQ_SEQUENCE,
    CLEAR_DLQ,
    PAUSE_PROCESSOR,
    START_PROCESSOR,
    RESET_PROCESSOR,
    SPLIT_SEGMENT,
    MERGE_SEGMENT,
    CANCEL_SCHEDULED_EVENT
}
