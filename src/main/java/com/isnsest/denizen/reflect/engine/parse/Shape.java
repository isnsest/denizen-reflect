package com.isnsest.denizen.reflect.engine.parse;

import com.isnsest.denizen.reflect.engine.EvalContext;

import java.util.ArrayList;
import java.util.List;

public record Shape(String key, List<Token> tokens, Object[] params) {

    private static final char SEP = '\u0001';
    private static final char HOLE = '\u0002';

    public static Shape of(List<Token> tokens) {
        StringBuilder key = null;
        List<Token> out = null;
        List<Object> params = null;
        int bracketDepth = 0;

        for (int i = 0, n = tokens.size(); i < n; i++) {
            Token t = tokens.get(i);
            TokenType type = t.type();
            if (type == TokenType.LEFT_BRACKET) bracketDepth++;
            else if (type == TokenType.RIGHT_BRACKET && bracketDepth > 0) bracketDepth--;

            char typeCode = bracketDepth == 0 ? typeCode(t) : 0;
            if (typeCode != 0) {
                if (key == null) {
                    key = new StringBuilder(64);
                    out = new ArrayList<>(n);
                    params = new ArrayList<>(4);
                    for (int j = 0; j < i; j++) {
                        Token prev = tokens.get(j);
                        key.append(prev.lexeme()).append(SEP);
                        out.add(prev);
                    }
                }
                key.append(HOLE).append(typeCode).append(SEP);
                out.add(t.withSlot(params.size()));
                params.add(t.literal());
            } else if (key != null) {
                key.append(t.lexeme()).append(SEP);
                out.add(t);
            }
        }

        if (key == null) {
            return new Shape(null, tokens, EvalContext.NO_PARAMS);
        }
        return new Shape(key.toString(), out, params.toArray());
    }

    public boolean hasParams() {
        return key != null;
    }

    private static char typeCode(Token t) {
        if (t.type() == TokenType.STRING) return 'S';
        if (t.type() != TokenType.NUMBER) return 0;
        Object v = t.literal();
        if (v instanceof Integer) return 'I';
        if (v instanceof Long) return 'J';
        if (v instanceof Double) return 'D';
        if (v instanceof Float) return 'F';
        return 0;
    }
}
