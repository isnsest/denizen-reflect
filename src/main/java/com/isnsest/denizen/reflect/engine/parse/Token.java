package com.isnsest.denizen.reflect.engine.parse;

public record Token(TokenType type, String lexeme, Object literal, int slot) {

    Token(TokenType type, String lexeme, Object literal) {
        this(type, lexeme, literal, -1);
    }

    Token withSlot(int slot) {
        return new Token(type, lexeme, literal, slot);
    }
}
