package com.example.game;

/** 這一局不能這樣做。訊息可以直接給使用者看。 */
public final class GameRuleException extends RuntimeException {

    public GameRuleException(String message) {
        super(message);
    }
}
