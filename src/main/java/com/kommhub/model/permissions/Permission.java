package com.kommhub.model.permissions;

/**
 * Canonical permission names, mirrored from the installation's permission enum. The hub does not evaluate
 * permissions itself — it proxies checks to the installation by name — but using this enum at call sites keeps
 * permission references type-safe and refactorable instead of relying on string literals.
 */
public enum Permission {
    DELETE_SERVER,
    EDIT_SERVER_INFO,
    EDIT_SERVER_PERMS,
    VIEW_SERVER_SETTINGS,
    CREATE_CHANNELS,
    DELETE_CHANNELS,
    EDIT_CHANNELS,
    EDIT_CHANNEL_PERMS,
    DELETE_INVITES,
    BAN_USERS,
    KICK_USERS,
    MUTE_USERS,
    DEAFEN_USERS,
    INVITE_USERS,
    POKE_USERS,
    CHECK_PING,
    CONTROL_MUSIC_BOT,
    SEND_MESSAGES,
    SEND_GIFS,
    SEND_ATTACHMENTS,
    ADD_REACTIONS,
    DELETE_OTHERS_MSGS,
    JOIN_VOICE,
    SCREEN_SHARE,
    USE_SOUNDBOARD,
    MANAGE_SERVER_SOUNDBOARD,
    MOVE_MEMBERS,
    VIEW_CHANNEL
}
