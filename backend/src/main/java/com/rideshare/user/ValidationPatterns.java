package com.rideshare.user;

/** Shared regular expressions for user input validation. */
public final class ValidationPatterns {

    /** Optional leading +, then 10 to 15 digits. Empty string is treated as "no phone". */
    public static final String PHONE = "^$|^\\+?[0-9]{10,15}$";

    /** At least one letter and one digit (length is checked separately). */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    public static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";

    private ValidationPatterns() {
    }
}
