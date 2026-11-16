package com.isnsest.denizen.reflect.engine.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Lexer {

    private static final Set<String> PRIMITIVES = Set.of("int", "long", "double", "float", "short", "byte", "char");

    private final String source;
    private final int length;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;

    public Lexer(String source) {
        this.source = source;
        this.length = source.length();
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= length;
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= length ? '\0' : source.charAt(current + 1);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        tokens.add(new Token(type, source.substring(start, current), literal));
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LEFT_PAREN);
            case ')' -> addToken(TokenType.RIGHT_PAREN);
            case '[' -> addToken(TokenType.LEFT_BRACKET);
            case ']' -> addToken(TokenType.RIGHT_BRACKET);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '=' -> addToken(TokenType.EQUALS);
            case '-' -> minus();
            case '"', '\'' -> string(c);
            case ' ', '\r', '\t', '\n' -> {
            }
            default -> {
                if (isDigit(c)) number();
                else if (isAlpha(c)) identifier();
            }
        }
    }

    private void minus() {
        if (peek() == '>') {
            advance();
            addToken(TokenType.ARROW);
        } else if (isDigit(peek()) && expectsValue()) {
            number();
        } else {
            addToken(TokenType.MINUS);
        }
    }

    private boolean expectsValue() {
        int n = tokens.size();
        if (n == 0) return true;
        TokenType prev = tokens.get(n - 1).type();
        if (prev == TokenType.LEFT_PAREN || prev == TokenType.COMMA || prev == TokenType.EQUALS
                || prev == TokenType.ARROW || prev == TokenType.LEFT_BRACKET) {
            return true;
        }
        return prev == TokenType.RIGHT_PAREN && n >= 3
                && tokens.get(n - 3).type() == TokenType.LEFT_PAREN
                && tokens.get(n - 2).type() == TokenType.IDENTIFIER
                && PRIMITIVES.contains(tokens.get(n - 2).lexeme());
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$' || c == '@' || c == '-' || c == '|';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void string(char quote) {
        while (!isAtEnd() && peek() != quote) {
            if (peek() == '\\' && current + 1 < length) current += 2;
            else advance();
        }
        if (isAtEnd()) throw new RuntimeException("Unterminated string literal");
        advance();
        String raw = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, raw.replace("\\" + quote, String.valueOf(quote)).replace("\\\\", "\\"));
    }

    private void number() {
        while (isDigit(peek())) advance();
        boolean fractional = false;
        if (peek() == '.' && isDigit(peekNext())) {
            fractional = true;
            advance();
            while (isDigit(peek())) advance();
        }
        if (isExponentStart()) {
            fractional = true;
            advance();
            if (peek() == '-' || peek() == '+') advance();
            while (isDigit(peek())) advance();
        }
        String text = source.substring(start, current);

        char suffix = Character.toLowerCase(peek());
        if ((suffix == 'l' || suffix == 'f' || suffix == 'd') && !isAlpha(peekNext()) && !isDigit(peekNext())) {
            advance();
            addToken(TokenType.NUMBER, suffixedLiteral(text, suffix, fractional));
            return;
        }

        addToken(TokenType.NUMBER, fractional ? (Object) Double.parseDouble(text) : integerLiteral(text));
    }

    private static Object suffixedLiteral(String text, char suffix, boolean fractional) {
        if (suffix == 'f') return Float.parseFloat(text);
        if (suffix == 'd') return Double.parseDouble(text);
        if (fractional) return (long) Double.parseDouble(text);
        return Long.parseLong(text);
    }

    private boolean isExponentStart() {
        char c = peek();
        if (c != 'e' && c != 'E') return false;
        char next = peekNext();
        if (isDigit(next)) return true;
        return (next == '-' || next == '+') && current + 2 < length && isDigit(source.charAt(current + 2));
    }

    private static Object integerLiteral(String text) {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            return Double.parseDouble(text);
        }
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
        return value;
    }

    private void identifier() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == '.' || c == '(' || c == ')' || c == '[' || c == ']' || c == ','
                    || c == '-' || c == '>' || Character.isWhitespace(c)) break;
            advance();
        }
        switch (source.substring(start, current)) {
            case "new" -> addToken(TokenType.NEW);
            case "true" -> addToken(TokenType.TRUE, Boolean.TRUE);
            case "false" -> addToken(TokenType.FALSE, Boolean.FALSE);
            case "null" -> addToken(TokenType.NULL);
            default -> addToken(TokenType.IDENTIFIER);
        }
    }
}
