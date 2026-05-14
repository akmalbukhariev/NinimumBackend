package com.ninimum.api.common;

public enum Result {
    SUCCESS(100, "Success."),
    FAILED(101, "Failed."),

    USER_EXIST(140, "User exist"),
    USER_NOT_EXIST(141, "User is not exist"),
    COMPANY_NOT_EXIST(142, "Company is not exist"),
    COMPANY_EXIST(143, "Company is exist"),
    BRANCH_NOT_EXIST(144, "Branch is not exist"),
    BRANCH_EXIST(145, "Branch is exist"),
    POSTER_EXIST(146, "Poster is exist"),
    POSTER_NOT_EXIST(147, "Poster is not exist"),
    POSTER_COMMENT_NOT_EXIST(148, "Poster comment is exist"),
    POSTER_COMMENT_EXIST(149, "Poster comment is exist"),
    POSTER_FEEDBACK_EXIST(150, "Poster feedback is exist"),
    POSTER_FEEDBACK_NOT_EXIST(151, "Poster feedback is not exist"),
    PROMOTION_EXIST(152, "Promotion is exist"),
    PROMOTION_NOT_EXIST(153, "Promotion is not exist"),
    USER_PASSWORD_NOT_MATCHED(154, "Password is not matched!"),
    NOT_FOUND(155, "Not found!"),
    FOUND(156, "Found!"),
    BLOCK_USER(157, "User blocked!"),
    DELETE_USER(158, "User deleted!"),

    TOKEN_INVALID(200, "Invalid token information."),
    TOKEN_EXPIRED_TIME(201, "This token is expired."),
    TOKEN_UNSUPPORTED_JWT(202, "Unsupported token information."),
    LOGIN_INVALID_TOKEN(250, "Token information cannot be verified."),
    LOGIN_DUPLICATE(251, "Duplicate login."),
    LOGIN(252, "Please log in first."),
    LOGIN_INACTIVE(253, "Please log in first."),
    LOGIN_BANNED(254, "User is banned. Access denied."),
    PASSWORD_IS_NOT_MATCHED(255, "Password is not matched"),

    VERIFY_PHONE_NUMBER_FAILED(256, "The phone verification failed."),

    AUTHENTICATION_ERROR(300, "Your authentication information cannot be verified."),
    //PROMOTION_IS_NOT_REGISTRATED(301, "The promotion is not registered for the users."),
    INTERNAL_ERROR(301, "Something went wrong on our end. We're working to fix it."),
    SERVER_ERROR(302, "A system error has occurred. Please contact your administrator."),
    TOKEN_EMPTY(360, "Empty token"),

    ROLE_INVALID(410, "Role is invalid");

    private int code;
    private String message;

    private Result(int code, String msg) {
        this.code = code;
        this.message = msg;
    }

    public String getCodeToString() {
        return Integer.toString(this.code);
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    public Result getResult(int code) {
        Result[] var2 = values();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Result result = var2[var4];
            if (result.getCode() == code) {
                return result;
            }
        }

        return null;
    }
}
