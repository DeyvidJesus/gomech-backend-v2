package com.gomech.api.modules.iam.domain;

/**
 * Account status of an IAM user.
 *
 * <p>This type names a concept that already existed in the module as the bare literal
 * {@code "ACTIVE"}, duplicated between the user entity default and the login check. It introduces
 * no new rule: only an active user may authenticate, exactly as before.
 *
 * <p>The status is still persisted as a {@code String} column, so this type deliberately works with
 * the raw value instead of forcing a mapping change on the entity.
 */
public final class UserStatus {

    /** The only status IAM behaviour currently distinguishes by name. */
    public static final String ACTIVE = "ACTIVE";

    private UserStatus() {
    }

    /** Whether the given persisted status represents an account allowed to authenticate. */
    public static boolean isActive(String status) {
        return ACTIVE.equals(status);
    }
}
