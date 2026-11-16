package com.isnsest.denizen.reflect.engine.parse;

import com.isnsest.denizen.reflect.engine.ast.*;

import java.util.ArrayList;
import java.util.List;

public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Node parse() { return expression(); }

    public Node parseFully() {
        Node node = expression();
        if (isAtEnd()) throw new RuntimeException("Unexpected token: " + peek().lexeme());
        return node;
    }

    private Node expression() {
        if (check(TokenType.IDENTIFIER) && peekNext().type() == TokenType.EQUALS) {
            String name = consume(TokenType.IDENTIFIER, "Expected identifier").lexeme();
            consume(TokenType.EQUALS, "Expected '='");
            Node value = expression();
            return new AssignmentNode(name, value);
        }
        if (isLambdaStart()) {
            return parseLambda();
        }
        return parseCast();
    }

    private Node parseCast() {
        if (isLookaheadCast()) {
            consume(TokenType.LEFT_PAREN, null);
            String typeName = qualifiedName("Expected type name");
            consume(TokenType.RIGHT_PAREN, null);

            Node target = parseCast();
            return new CastNode(typeName, target);
        }
        return postfix();
    }

    private String qualifiedName(String error) {
        StringBuilder sb = new StringBuilder(consume(TokenType.IDENTIFIER, error).lexeme());
        while (check(TokenType.DOT) && peekNext().type() == TokenType.IDENTIFIER) {
            advance();
            sb.append('.').append(advance().lexeme());
        }
        return sb.toString();
    }

    private boolean isLookaheadCast() {
        int i = current;
        if (i >= tokens.size() || tokens.get(i).type() != TokenType.LEFT_PAREN) return false;
        i++;
        if (i >= tokens.size() || tokens.get(i).type() != TokenType.IDENTIFIER) return false;
        i++;
        while (i + 1 < tokens.size() && tokens.get(i).type() == TokenType.DOT
                && tokens.get(i + 1).type() == TokenType.IDENTIFIER) {
            i += 2;
        }
        if (i >= tokens.size() || tokens.get(i).type() != TokenType.RIGHT_PAREN) return false;
        i++;
        if (i >= tokens.size()) return false;

        TokenType nextType = tokens.get(i).type();
        return nextType == TokenType.IDENTIFIER ||
                nextType == TokenType.NUMBER ||
                nextType == TokenType.STRING ||
                nextType == TokenType.TRUE ||
                nextType == TokenType.FALSE ||
                nextType == TokenType.NULL ||
                nextType == TokenType.NEW ||
                nextType == TokenType.LEFT_PAREN ||
                nextType == TokenType.LEFT_BRACKET ||
                nextType == TokenType.MINUS;
    }

    private Token peekNext() {
        if (current + 1 >= tokens.size()) return tokens.getLast();
        return tokens.get(current + 1);
    }

    private boolean isLambdaStart() {
        int save = current;
        try {
            if (check(TokenType.IDENTIFIER)) {
                advance();
                return check(TokenType.ARROW) || check(TokenType.MINUS);
            }
            if (check(TokenType.LEFT_PAREN)) {
                advance();
                if (check(TokenType.RIGHT_PAREN)) {
                    advance();
                    return check(TokenType.ARROW) || check(TokenType.MINUS);
                }
                while (check(TokenType.IDENTIFIER)) {
                    advance();
                    if (check(TokenType.COMMA)) advance();
                    else break;
                }
                if (check(TokenType.RIGHT_PAREN)) {
                    advance();
                    return check(TokenType.ARROW) || check(TokenType.MINUS);
                }
            }
        } finally {
            current = save;
        }
        return false;
    }

    private Node parseLambda() {
        List<String> params = new ArrayList<>();

        if (match(TokenType.IDENTIFIER)) {
            params.add(previous().lexeme());
        } else {
            consume(TokenType.LEFT_PAREN, "Expected '(' for lambda params");
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    params.add(consume(TokenType.IDENTIFIER, "Expected lambda parameter name").lexeme());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_PAREN, "Expected ')' for lambda params");
        }

        if (!match(TokenType.ARROW) && !match(TokenType.MINUS)) {
            throw new RuntimeException("Expected '->' or '-' for lambda");
        }

        List<Node> statements = new ArrayList<>();
        do {
            statements.add(expression());
        } while (isAtEnd() && !check(TokenType.RIGHT_PAREN) && !check(TokenType.COMMA));

        Node body = statements.size() == 1 ? statements.getFirst() : new BlockNode(statements);

        return new LambdaNode(params, body);
    }

    private Node postfix() {
        Node node = primary();
        while (true) {
            if (match(TokenType.DOT)) {
                Token nameTok = consume(TokenType.IDENTIFIER, "Expected identifier after '.'");
                String name = nameTok.lexeme();
                if (match(TokenType.LEFT_PAREN)) {
                    List<Node> args = argumentList();
                    node = new MethodCallNode(node, name, args);
                } else {
                    node = new FieldAccessNode(node, name);
                }
                continue;
            }
            if (match(TokenType.LEFT_BRACKET)) {
                String inside = readBracketRawString();
                node = new BracketInitNode(node, inside);
                continue;
            }
            break;
        }
        return node;
    }

    private String readBracketRawString() {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (isAtEnd() && depth > 0) {
            Token t = advance();
            if (t.type() == TokenType.LEFT_BRACKET) depth++;
            else if (t.type() == TokenType.RIGHT_BRACKET) {
                depth--;
                if (depth == 0) break;
            }
            sb.append(t.lexeme());
        }
        if (depth > 0) throw new RuntimeException("Unterminated '[' literal");
        return sb.toString().trim();
    }

    private List<Node> argumentList() {
        List<Node> args = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do { args.add(expression()); } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments");
        return args;
    }

    private Node primary() {
        if (match(TokenType.NUMBER, TokenType.STRING, TokenType.TRUE, TokenType.FALSE, TokenType.NULL)) {
            Token t = previous();
            return t.slot() >= 0 ? new ParamNode(t.slot()) : new LiteralNode(t.literal());
        }
        if (match(TokenType.NEW)) {
            String typeName = qualifiedName("Expected type name after 'new'");
            consume(TokenType.LEFT_PAREN, "Expected '(' after type name");
            List<Node> args = argumentList();
            return new NewNode(typeName, args);
        }
        if (match(TokenType.IDENTIFIER)) return new VariableNode(previous().lexeme());
        if (match(TokenType.LEFT_PAREN)) {
            Node expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression");
            return expr;
        }
        throw new RuntimeException("Unexpected token: " + peek().lexeme());
    }

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { advance(); return true; }
        }
        return false;
    }

    private Token consume(TokenType type, String msg) {
        if (check(type)) return advance();
        throw new RuntimeException(msg);
    }

    private boolean check(TokenType type) { return isAtEnd() && peek().type() == type; }
    private Token advance() { if (isAtEnd()) current++; return previous(); }
    private boolean isAtEnd() { return peek().type() != TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
}
