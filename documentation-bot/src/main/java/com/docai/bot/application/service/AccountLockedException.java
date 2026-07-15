package com.docai.bot.application.service;

/** Thrown by {@link UserService#authenticate} when an account is temporarily locked out after too
 * many consecutive failed login attempts — deliberately not an {@link IllegalArgumentException}
 * so {@code AuthController} can map it to 423 Locked instead of the generic 401. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
