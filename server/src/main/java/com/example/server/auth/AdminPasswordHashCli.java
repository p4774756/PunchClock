package com.example.server.auth;

import org.mindrot.jbcrypt.BCrypt;

/** 在本機由明文產生 {@code ADMIN_PASSWORD} 用的 bcrypt 雜湊；勿在 CI 或 log 中印出結果與引數。 */
public final class AdminPasswordHashCli {

    private AdminPasswordHashCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("用法: AdminPasswordHashCli <明文密碼>");
            System.exit(1);
        }
        System.out.println(BCrypt.hashpw(args[0], BCrypt.gensalt(12)));
    }
}