package com.isnsest.denizen.reflect.engine;

import com.isnsest.denizen.reflect.engine.ast.Node;
import com.isnsest.denizen.reflect.engine.parse.Lexer;
import com.isnsest.denizen.reflect.engine.parse.Parser;
import com.isnsest.denizen.reflect.engine.parse.Shape;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ExpressionCache {

    record Compiled(Node root, Object[] params) {
    }

    private static final int MAX_EXACT = 512;
    private static final int MAX_SHAPES = 1024;

    private static final Compiled NOT_AN_EXPRESSION = new Compiled(null, null);
    private static final RuntimeException NOT_AN_EXPRESSION_ERROR = new RuntimeException("Not a complete expression", null, false, false) {};

    private final Map<String, Compiled> exact = new ConcurrentHashMap<>();
    private final Map<String, Node> shapes = new ConcurrentHashMap<>();

    Compiled get(String expression, boolean strict) {
        Compiled compiled = exact.get(expression);
        if (compiled != null) {
            if (compiled == NOT_AN_EXPRESSION) throw NOT_AN_EXPRESSION_ERROR;
            return compiled;
        }

        Shape shape;
        Node root;
        try {
            shape = Shape.of(new Lexer(expression).tokenize());
            root = shapeTree(shape.hasParams() ? shape.key() : expression, shape, strict);
        } catch (RuntimeException e) {
            if (strict) putExact(expression, NOT_AN_EXPRESSION);
            throw e;
        }

        compiled = new Compiled(root, shape.params());
        putExact(expression, compiled);
        return compiled;
    }

    void clear() {
        exact.clear();
        shapes.clear();
    }

    private Node shapeTree(String key, Shape shape, boolean strict) {
        Node root = shapes.get(key);
        if (root != null) return root;

        Parser parser = new Parser(shape.tokens());
        Node parsed = strict ? parser.parseFully() : parser.parse();
        if (shapes.size() >= MAX_SHAPES) evictQuarter(shapes);
        Node prev = shapes.putIfAbsent(key, parsed);
        return prev != null ? prev : parsed;
    }

    private void putExact(String expression, Compiled compiled) {
        if (exact.size() >= MAX_EXACT) exact.clear();
        exact.put(expression, compiled);
    }

    private static void evictQuarter(Map<String, ?> map) {
        int toRemove = Math.max(1, map.size() / 4);
        Iterator<String> it = map.keySet().iterator();
        while (toRemove-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
