package lk.customs.rms.enums;

/**
 * IMPORTANT:
 * - Stored in DB as STRING (EnumType.STRING)
 * - Never rename existing values (history would break)
 * - Only add new values when extending workflow
 */
public enum MovementActionType {
    CREATE,
    UPDATE,
    FORWARD,
    RETURN,
    APPROVE,
    REJECT,
    ISSUE,

    // NEW: DC can reopen APPROVED/REJECTED back to IN_PROGRESS (not allowed for ISSUED)
    REOPEN
}
