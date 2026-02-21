package com.isnsest.denizen.reflect.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.JavaReflectedObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.tags.core.EscapeTagUtil;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import meigo.denizen.DenizenTagFinder;

public final class JavaExpressionEngine {

    private JavaExpressionEngine() {
    }

    public static final JavaExpressionEngine INSTANCE = new JavaExpressionEngine();

    private static final int MAX_CACHE_SIZE = 2000;

    private static final Map<String, Node> parsedExpressionCache = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> classLookupCache = new ConcurrentHashMap<>();
    private static final Class<?> CLASS_NOT_FOUND_MARKER = Void.class;

    private static void checkCacheSize() {
        if (parsedExpressionCache.size() > MAX_CACHE_SIZE) {
            parsedExpressionCache.clear();
        }
    }

    public static void importClass(String path, String className, String alias) throws ClassNotFoundException {
        INSTANCE.doImportClass(path, className, alias);
    }

    public static void clearAllImports() {
        importContexts.clear();
        classLookupCache.clear();
        parsedExpressionCache.clear();
        ReflectionUtil.clearCache();
    }

    public static String unescape(String expression) {
        if (expression.contains("ƈ") || expression.contains("Ţ")) {
            expression = expression
                    .replace("ƈ", ",")
                    .replace("Ţ", "-");
        }
        return EscapeTagUtil.unEscape(expression);
    }

    public static Object execute(String expression, ScriptEntry scriptEntry) {
        String path = "";
        if (scriptEntry.getScript() != null) {
            path = scriptEntry.getScript().getContainer().getRelativeFileName().replace("\\", "/");
        }
        int scriptIdx = path.indexOf("scripts/");
        if (scriptIdx != -1) {
            path = path.substring(scriptIdx + "scripts/".length());
        }

        List<String> tags = DenizenTagFinder.findTags(expression);
        if (!tags.isEmpty()) {
            for (String string : tags) {
                expression = expression.replace(string, EscapeTagUtil.escape(string)
                        .replace(",", "ƈ")
                        .replace("-", "Ţ")
                );
            }
        }

        try {
            return INSTANCE.doExecute(expression, scriptEntry, path);
        } catch (Throwable t) {
            if (t instanceof NullPointerException || t.getMessage() == null) {
                return null;
            }
            Debug.echoError("Error evaluating Java expression: " + unescape(expression));
            Debug.echoError(t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    public static boolean isSimple(Object obj) {
        if (obj == null) return false;
        if (obj instanceof ObjectTag) { return true; }
        if (obj instanceof List || obj instanceof Map) { return true; }
        if (obj.getClass() == Collections.EMPTY_SET.getClass()) return true;
        if (obj.getClass().getName().startsWith("java.util.Collections$Unmodifiable")) { return true; }
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }

    public static ObjectTag wrapObject(Object result, TagContext context) {
        if (isSimple(result)) {
            return CoreUtilities.objectToTagForm(result, context);
        }
        return new JavaReflectedObjectTag(result);
    }

    public static Map<String, ImportContext> importContexts = new ConcurrentHashMap<>();

    private void doImportClass(String path, String className, String alias) throws ClassNotFoundException {
        String keyPath = (path == null || path.isEmpty()) ? "<global>" : path;
        ImportContext ctx = importContexts.computeIfAbsent(keyPath, p -> new ImportContext());

        
        if (className.endsWith(".*")) {
            String packageName = className.substring(0, className.length() - 2); 
            ctx.addStarImport(packageName);
            return;
        }

        Class<?> cls = resolveClass(className);
        
        String key = (alias != null && !alias.isEmpty()) ? alias : cls.getSimpleName();
        ctx.addImport(key, cls);
    }

    private Object doExecute(String expression, ScriptEntry scriptEntry, String path) throws Throwable {
        if (expression == null) return null;
        expression = expression.trim();

        if (expression.length() >= 2 && expression.charAt(0) == '%' && expression.charAt(expression.length() - 1) == '%') {
            if (expression.length() == 2) return "%";
            String inner = expression.substring(1, expression.length() - 1).trim();
            return doExecute(inner, scriptEntry, path);
        }

        String keyPath = (path == null || path.isEmpty()) ? "<global>" : path;
        ImportContext imports = importContexts.getOrDefault(keyPath, ImportContext.EMPTY);
        EvalContext ctx = new EvalContext(imports, scriptEntry);

        if (expression.indexOf('%') >= 0) {
            Object templated = evalTemplate(expression, ctx);
            if (isSimple(templated)) {
                return CoreUtilities.objectToTagForm(templated, scriptEntry.context);
            } else {
                return new JavaReflectedObjectTag(templated);
            }
        }

        Node root = parsedExpressionCache.computeIfAbsent(expression, k -> {
            checkCacheSize();
            return new Parser(k).parse();
        });

        Object result = root.eval(ctx);
        return wrapObject(result, scriptEntry.context);
    }

    private Object evalTemplate(String template, EvalContext ctx) throws Throwable {
        StringBuilder out = new StringBuilder();
        int idx = 0;
        int len = template.length();

        while (idx < len) {
            int start = template.indexOf('%', idx);
            if (start < 0) {
                out.append(template, idx, len);
                break;
            }

            if (start + 1 < len && template.charAt(start + 1) == '%') {
                out.append(template, idx, start).append('%');
                idx = start + 2;
                continue;
            }

            int end = template.indexOf('%', start + 1);
            if (end < 0) {
                out.append(template, idx, len);
                break;
            }

            out.append(template, idx, start);

            String inner = template.substring(start + 1, end).trim();
            if (!inner.isEmpty()) {
                if (inner.startsWith("<") && inner.endsWith(">")) {
                    inner = inner.substring(1, inner.length() - 1);
                }

                Node expr = parsedExpressionCache.computeIfAbsent(inner, k -> new Parser(k).parse());

                Object val = expr.eval(ctx);

                if (val instanceof ObjectTag) {
                    val = ((ObjectTag) val).getJavaObject();
                }
                out.append(val == null ? "null" : String.valueOf(val));
            }

            idx = end + 1;
        }
        return out.toString();
    }

    public static class Lambda {
        final List<String> paramNames;
        final Node body;
        final EvalContext closureContext;

        public Lambda(List<String> paramNames, Node body, EvalContext closureContext) {
            this.paramNames = paramNames;
            this.body = body;
            this.closureContext = closureContext;
        }
    }

    private static final class LambdaNode extends Node {
        private final List<String> paramNames;
        private final Node body;

        LambdaNode(List<String> paramNames, Node body) {
            this.paramNames = paramNames;
            this.body = body;
        }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            return new Lambda(paramNames, body, ctx);
        }
    }

    public static final class ImportContext {
        static final ImportContext EMPTY = new ImportContext(Collections.emptyMap());
        public final Map<String, Class<?>> imports;
        public final Set<String> starImports;

        ImportContext() {
            this.imports = new ConcurrentHashMap<>();
            this.starImports = Collections.newSetFromMap(new ConcurrentHashMap<>());
        }

        ImportContext(Map<String, Class<?>> imports) {
            this.imports = imports;
            this.starImports = Collections.emptySet();
        }

        void addImport(String alias, Class<?> cls) {
            imports.put(alias, cls);
        }

        void addStarImport(String packageName) {
            starImports.add(packageName);
        }

        public Class<?> resolveType(String name) {
            
            Class<?> cls = imports.get(name);
            if (cls != null) return cls;

            
            
            if (!starImports.isEmpty()) {
                for (String pkg : starImports) {
                    try {
                        return resolveClass(pkg + "." + name);
                    } catch (ClassNotFoundException ignored) {
                        
                    }
                }
            }
            return null;
        }
    }

    public record EvalContext(ImportContext imports, ScriptEntry scriptEntry, Map<String, Object> locals) {
        public EvalContext(ImportContext imports, ScriptEntry scriptEntry) {
            this(imports, scriptEntry, Collections.emptyMap());
        }
    }

    private record Token(TokenType type, String lexeme, Object literal) {
    }

    private enum TokenType {
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET,
        COMMA, DOT, IDENTIFIER, NUMBER, STRING,
        NEW, TRUE, FALSE, NULL, EOF,
        MINUS, ARROW, EQUALS
    }

    private static final class Lexer {
        private final String source;
        private final int length;
        private int start = 0;
        private int current = 0;
        private final List<Token> tokens = new ArrayList<>();

        Lexer(String source) {
            this.source = source;
            this.length = source.length();
        }

        List<Token> tokenize() {
            while (!isAtEnd()) {
                start = current;
                scanToken();
            }
            tokens.add(new Token(TokenType.EOF, "", null));
            return tokens;
        }

        private boolean isAtEnd() { return current >= length; }
        private char advance() { return source.charAt(current++); }
        private char peek() { return isAtEnd() ? '\0' : source.charAt(current); }
        private char peekNext() { return (current + 1 >= length) ? '\0' : source.charAt(current + 1); }

        private void addToken(TokenType type) { addToken(type, null); }
        private void addToken(TokenType type, Object literal) {
            String text = source.substring(start, current);
            tokens.add(new Token(type, text, literal));
        }

        private void scanToken() {
            char c = advance();
            switch (c) {
                case '(': addToken(TokenType.LEFT_PAREN); return;
                case ')': addToken(TokenType.RIGHT_PAREN); return;
                case '[': addToken(TokenType.LEFT_BRACKET); return;
                case ']': addToken(TokenType.RIGHT_BRACKET); return;
                case ',': addToken(TokenType.COMMA); return;
                case '.': addToken(TokenType.DOT); return;
                case '=': addToken(TokenType.EQUALS); return;
                case '-':
                    if (peek() == '>') {
                        advance();
                        addToken(TokenType.ARROW);
                    } else {
                        addToken(TokenType.MINUS);
                    }
                    return;
                case ' ': case '\r': case '\t': case '\n': return;
                case '"': string(); return;
                default:
                    if (isDigit(c)) { number(); return; }
                    if (isAlpha(c)) { identifier(); }
            }
        }

        private boolean isAlpha(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$' || c == '@' || c == '-' || c == '|';
        }
        private boolean isDigit(char c) { return c >= '0' && c <= '9'; }

        private void string() {
            while (!isAtEnd() && peek() != '"') {
                if (peek() == '\\' && current + 1 < length) current += 2;
                else advance();
            }
            if (isAtEnd()) throw new RuntimeException("Unterminated string literal");
            advance();
            String raw = source.substring(start + 1, current - 1);
            addToken(TokenType.STRING, raw.replace("\\\"", "\"").replace("\\\\", "\\"));
        }

        private void number() {
            while (isDigit(peek())) advance();
            if (peek() == '.' && isDigit(peekNext())) {
                advance();
                while (isDigit(peek())) advance();
            }
            String text = source.substring(start, current);
            Object literal;
            if (text.indexOf('.') >= 0) literal = Double.parseDouble(text);
            else {
                try {
                    long l = Long.parseLong(text);
                    literal = (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? (int) l : l;
                } catch (NumberFormatException e) {
                    literal = Double.parseDouble(text);
                }
            }
            addToken(TokenType.NUMBER, literal);
        }

        private void identifier() {
            while (!isAtEnd()) {
                char c = peek();
                if (c == '.' || c == '(' || c == ')' || c == '[' || c == ']' || c == ',' || Character.isWhitespace(c) || c == '-' || c == '>') break;
                advance();
            }
            String text = source.substring(start, current);
            switch (text) {
                case "new": addToken(TokenType.NEW); return;
                case "true": addToken(TokenType.TRUE, Boolean.TRUE); return;
                case "false": addToken(TokenType.FALSE, Boolean.FALSE); return;
                case "null": addToken(TokenType.NULL, null); return;
                default: addToken(TokenType.IDENTIFIER);
            }
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int current = 0;

        Parser(String source) {
            this.tokens = new Lexer(source).tokenize();
        }

        Node parse() { return expression(); }

        private Node expression() {
            if (check(TokenType.IDENTIFIER) && peekNext().type == TokenType.EQUALS) {
                String name = consume(TokenType.IDENTIFIER, "Expected identifier").lexeme;
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
                String typeName = consume(TokenType.IDENTIFIER, "Expected type name").lexeme;
                consume(TokenType.RIGHT_PAREN, null);

                Node target = parseCast();
                return new CastNode(typeName, target);
            }
            return postfix();
        }

        private boolean isLookaheadCast() {
            if (current + 3 >= tokens.size()) return false;

            if (tokens.get(current).type != TokenType.LEFT_PAREN) return false;
            if (tokens.get(current + 1).type != TokenType.IDENTIFIER) return false;
            if (tokens.get(current + 2).type != TokenType.RIGHT_PAREN) return false;

            TokenType nextType = tokens.get(current + 3).type;
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
            if (current + 1 >= tokens.size()) return tokens.get(tokens.size() - 1);
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
                params.add(previous().lexeme);
            } else {
                consume(TokenType.LEFT_PAREN, "Expected '(' for lambda params");
                if (!check(TokenType.RIGHT_PAREN)) {
                    do {
                        params.add(consume(TokenType.IDENTIFIER, "Expected lambda parameter name").lexeme);
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
            } while (!isAtEnd() && !check(TokenType.RIGHT_PAREN) && !check(TokenType.COMMA));

            Node body = statements.size() == 1 ? statements.get(0) : new BlockNode(statements);

            return new LambdaNode(params, body);
        }

        private Node postfix() {
            Node node = primary();
            while (true) {
                if (match(TokenType.DOT)) {
                    Token nameTok = consume(TokenType.IDENTIFIER, "Expected identifier after '.'");
                    String name = nameTok.lexeme;
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
            while (!isAtEnd() && depth > 0) {
                Token t = advance();
                if (t.type == TokenType.LEFT_BRACKET) depth++;
                else if (t.type == TokenType.RIGHT_BRACKET) {
                    depth--;
                    if (depth == 0) break;
                }
                sb.append(t.lexeme);
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
                return new LiteralNode(previous().literal);
            }
            if (match(TokenType.NEW)) {
                String typeName = consume(TokenType.IDENTIFIER, "Expected type name after 'new'").lexeme;
                consume(TokenType.LEFT_PAREN, "Expected '(' after type name");
                List<Node> args = argumentList();
                return new NewNode(typeName, args);
            }
            if (match(TokenType.IDENTIFIER)) return new VariableNode(previous().lexeme);
            if (match(TokenType.LEFT_PAREN)) {
                Node expr = expression();
                consume(TokenType.RIGHT_PAREN, "Expected ')' after expression");
                return expr;
            }
            throw new RuntimeException("Unexpected token: " + peek().lexeme);
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
        private boolean check(TokenType type) { return !isAtEnd() && peek().type == type; }
        private Token advance() { if (!isAtEnd()) current++; return previous(); }
        private boolean isAtEnd() { return peek().type == TokenType.EOF; }
        private Token peek() { return tokens.get(current); }
        private Token previous() { return tokens.get(current - 1); }
    }

    private abstract static class Node {
        abstract Object eval(EvalContext ctx) throws Throwable;
    }

    private static final class LiteralNode extends Node {
        private final Object value;
        LiteralNode(Object value) { this.value = value; }
        @Override Object eval(EvalContext ctx) { return value; }
    }

    private static final class AssignmentNode extends Node {
        private final String name;
        private final Node valueExpression;

        AssignmentNode(String name, Node valueExpression) {
            this.name = name;
            this.valueExpression = valueExpression;
        }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object value = valueExpression.eval(ctx);
            ctx.locals.put(name, value);
            return value;
        }
    }

    private static final class CastNode extends Node {
        private final String typeName;
        private final Node target;

        CastNode(String typeName, Node target) {
            this.typeName = typeName;
            this.target = target;
        }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object value = target.eval(ctx);
            if (value == null) return null;

            Class<?> type;
            switch (typeName) {
                case "int": type = int.class; break;
                case "double": type = double.class; break;
                case "float": type = float.class; break;
                case "long": type = long.class; break;
                case "short": type = short.class; break;
                case "byte": type = byte.class; break;
                case "boolean": type = boolean.class; break;
                case "char": type = char.class; break;
                case "String": type = String.class; break;
                default:
                    type = ctx.imports.resolveType(typeName);
                    if (type == null) {
                        try {
                            type = resolveClass(typeName);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException("Unknown class for cast: " + typeName);
                        }
                    }
            }

            return ReflectionUtil.adaptArgument(type, value);
        }
    }

    private static final class BracketInitNode extends Node {
        private final Node target;
        private final String inside;
        private static final Pattern SPLIT_KV = Pattern.compile("[=:]");

        BracketInitNode(Node target, String inside) {
            this.target = target;
            this.inside = inside == null ? "" : inside.trim();
        }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object base = target.eval(ctx);
            if (base instanceof String name) {
                try {
                    Class<?> cls = resolveClass(name);
                    return evalJavaBracketLiteral(cls);
                } catch (ClassNotFoundException ex) {
                    try {
                        ObjectTag tag = ObjectFetcher.pickObjectFor(name + "[" + inside + "]", ctx.scriptEntry.context);
                        return tag.getJavaObject();
                    } catch (Exception ex2) {
                        return name + "[" + inside + "]";
                    }
                }
            }
            if (base instanceof Class<?>) {
                return evalJavaBracketLiteral((Class<?>) base);
            }
            throw new RuntimeException("Bracket literal requires class name or string on the left, got: " + base);
        }

        private Object evalJavaBracketLiteral(Class<?> cls) throws Throwable {
            if (inside.isEmpty()) {
                return ReflectionUtil.construct(cls, new Object[0]);
            }

            boolean named = inside.contains("=") || inside.contains(":");

            if (named) {
                Object obj = ReflectionUtil.construct(cls, new Object[0]);
                for (String part : splitTopLevel(inside)) {
                    String[] kv = SPLIT_KV.split(part, 2);
                    if (kv.length < 2) continue;
                    String fieldName = kv[0].trim();
                    String raw = kv[1].trim();

                    Field f = ReflectionUtil.findFieldDeep(cls, fieldName);
                    if (f == null) throw new RuntimeException("Field '" + fieldName + "' not found in " + cls.getName());

                    Object val = ReflectionUtil.adaptArgument(f.getType(), VariableNode.parseLiteral(f.getType(), raw));
                    f.setAccessible(true);
                    f.set(obj, val);
                }
                return obj;
            } else {
                List<String> parts = splitTopLevel(inside);
                Object[] attemptArgs = new Object[parts.size()];
                for (int i = 0; i < parts.size(); i++) {
                    attemptArgs[i] = VariableNode.parseLiteral(String.class, parts.get(i).trim());
                }
                return ReflectionUtil.construct(cls, attemptArgs);
            }
        }

        private static List<String> splitTopLevel(String s) {
            List<String> out = new ArrayList<>();
            int start = 0, depthPar = 0;
            boolean inStr = false;
            char strQ = 0;
            int len = s.length();

            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (inStr) {
                    if (c == strQ && s.charAt(i - 1) != '\\') inStr = false;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    inStr = true;
                    strQ = c;
                    continue;
                }
                if (c == '(') depthPar++;
                else if (c == ')') depthPar = Math.max(0, depthPar - 1);
                else if (depthPar == 0 && (c == ',' || c == ';')) {
                    out.add(s.substring(start, i).trim());
                    start = i + 1;
                }
            }
            out.add(s.substring(start).trim());
            return out;
        }
    }

    private static final class VariableNode extends Node {
        private final String originalName;
        VariableNode(String name) { this.originalName = name; }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            String name = unescape(this.originalName);

            if (ctx.locals.containsKey(name)) {
                return ctx.locals.get(name);
            }

            if (!name.contains("@")) {
                Class<?> imported = ctx.imports.resolveType(name);
                if (imported != null) return imported;
                try {
                    return resolveClass(name);
                } catch (ClassNotFoundException ignored) {}
            }

            if (name.equals("player")) {
                try { return Utilities.getEntryPlayer(ctx.scriptEntry).getJavaObject(); }
                catch (Exception ignored) {}
            }
            if (name.equals("scriptEntry")) return ctx.scriptEntry;

            try {
                if (ctx.scriptEntry != null && ctx.scriptEntry.getResidingQueue() != null
                        && ctx.scriptEntry.getResidingQueue().definitions.containsKey(name)) {
                    return ctx.scriptEntry.getResidingQueue().getDefinitionObject(name).getJavaObject();
                }
                if (ctx.scriptEntry != null && ctx.scriptEntry.getContext() != null
                        && ctx.scriptEntry.getContext().contextSource != null
                        && ctx.scriptEntry.getContext().contextSource.getContext(name) != null)
                { return ctx.scriptEntry.getContext().contextSource.getContext(name).getJavaObject(); }
            } catch (Exception ignored) {}

            try {
                ObjectTag result = ObjectFetcher.pickObjectFor(name, ctx.scriptEntry.context);
                return result.getJavaObject();
            } catch (Exception ignored) {}

            return name;
        }

        static Object parseLiteral(Class<?> type, String text) {
            if (text == null) return null;
            if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
                text = text.substring(1, text.length() - 1);
            }
            try {
                if (type == String.class) return text;
                if (type == int.class || type == Integer.class) return Integer.parseInt(text);
                if (type == boolean.class || type == Boolean.class) return text.equalsIgnoreCase("true") || text.equals("1");
                if (Number.class.isAssignableFrom(type)) return text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
                if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, text);
            } catch (Exception ignored) {}
            return text;
        }
    }

    public static Class<?> resolveClass(String name) throws ClassNotFoundException {
        Class<?> cached = classLookupCache.get(name);
        if (cached != null) {
            if (cached == CLASS_NOT_FOUND_MARKER) throw new ClassNotFoundException(name);
            return cached;
        }
        try {
            Class<?> cls = Class.forName(name, true, LibraryLoader.getClassLoader());
            classLookupCache.put(name, cls);
            return cls;
        } catch (ClassNotFoundException e) {
            classLookupCache.put(name, CLASS_NOT_FOUND_MARKER);
            throw e;
        }
    }

    private static final class NewNode extends Node {
        private final String typeName;
        private final List<Node> args;
        NewNode(String typeName, List<Node> args) { this.typeName = typeName; this.args = args; }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Class<?> type = ctx.imports.resolveType(typeName);
            if (type == null) type = resolveClass(typeName);
            Object[] values = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) values[i] = args.get(i).eval(ctx);
            return ReflectionUtil.construct(type, values);
        }
    }

    private static final class FieldAccessNode extends Node {
        private final Node targetNode;
        private final String fieldName;
        FieldAccessNode(Node targetNode, String fieldName) { this.targetNode = targetNode; this.fieldName = fieldName; }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object base = targetNode.eval(ctx);
            if (base instanceof String) {
                String full = base + "." + fieldName;
                try { return resolveClass(full); } catch (ClassNotFoundException ignored) { return full; }
            }
            if (base instanceof Class<?>) {
                if (fieldName.startsWith("[") && fieldName.endsWith("]")) {
                    return new BracketInitNode(new LiteralNode(base), fieldName.substring(1, fieldName.length()-1)).eval(ctx);
                }
                return ReflectionUtil.getField(base, fieldName);
            }
            if (base != null) {
                return ReflectionUtil.getField(base, fieldName);
            }
            throw new RuntimeException("Cannot access field '" + fieldName + "' on null target");
        }
    }

    private static final class MethodCallNode extends Node {
        private final Node target;
        private final String methodName;
        private final List<Node> args;
        MethodCallNode(Node target, String methodName, List<Node> args) { this.target = target; this.methodName = methodName; this.args = args; }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object obj = target.eval(ctx);
            Object[] values = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i).eval(ctx);
                if (arg instanceof String) arg = unescape((String) arg);
                values[i] = arg;
            }
            return ReflectionUtil.invokeMethod(obj, methodName, values);
        }
    }

    private static final class BlockNode extends Node {
        private final List<Node> statements;

        BlockNode(List<Node> statements) {
            this.statements = statements;
        }

        @Override
        Object eval(EvalContext ctx) throws Throwable {
            Object result = null;
            for (Node node : statements) {
                result = node.eval(ctx);
            }
            return result;
        }
    }

    public static final class ReflectionUtil {
        private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();
        private static final int MAX_REFLECTION_CACHE_SIZE = 10000;

        private static final Map<MemberKey, MethodHandle> METHOD_CACHE = new ConcurrentHashMap<>();
        private static final Map<MemberKey, MethodHandle> FIELD_CACHE = new ConcurrentHashMap<>();
        private static final Set<MemberKey> MISSING_MEMBERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private static final class MemberKey {
            final Class<?> clazz;
            final String name;
            final Class<?>[] paramTypes;
            final int hashCode;

            MemberKey(Class<?> clazz, String name, Class<?>[] paramTypes) {
                this.clazz = clazz;
                this.name = name;
                this.paramTypes = paramTypes;
                this.hashCode = Objects.hash(clazz, name) * 31 + Arrays.hashCode(paramTypes);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof MemberKey that)) return false;
                return hashCode == that.hashCode && clazz == that.clazz &&
                        name.equals(that.name) && Arrays.equals(paramTypes, that.paramTypes);
            }

            @Override
            public int hashCode() { return hashCode; }
        }

        public static void clearCache() {
            METHOD_CACHE.clear();
            FIELD_CACHE.clear();
            MISSING_MEMBERS.clear();
        }

        private static void checkCacheSize() {
            if (METHOD_CACHE.size() > MAX_REFLECTION_CACHE_SIZE) {
                clearCache();
            }
        }

        static Object construct(Class<?> type, Object[] args) throws Throwable {
            Class<?>[] argTypes = getTypes(args);
            MemberKey key = new MemberKey(type, "<init>", argTypes);
            MethodHandle handle = METHOD_CACHE.get(key);

            if (handle == null) {
                checkCacheSize();
                Constructor<?> ctor = findConstructorDeep(type, args);
                if (ctor == null) throw new NoSuchMethodException("No constructor for " + type.getName());

                ctor.setAccessible(true);
                handle = ROOT_LOOKUP.unreflectConstructor(ctor);
                
                METHOD_CACHE.put(key, handle);
            }

            boolean isVarargs = handle.type().parameterArray().length > 0 &&
                    handle.type().parameterArray()[handle.type().parameterCount() - 1].isArray() &&
                    (args.length >= handle.type().parameterCount() - 1);

            Object[] adaptedArgs = adaptArguments(handle.type().parameterArray(), args, isVarargs);
            return handle.invokeWithArguments(adaptedArgs);
        }

        static Object invokeMethod(Object targetOrClass, String name, Object[] args) throws Throwable {
            boolean isStatic = targetOrClass instanceof Class<?>;
            Class<?> owner = isStatic ? (Class<?>) targetOrClass : targetOrClass.getClass();
            Class<?>[] argTypes = getTypes(args);

            MemberKey key = new MemberKey(owner, name, argTypes);

            MethodHandle handle = METHOD_CACHE.get(key);

            if (handle == null && MISSING_MEMBERS.contains(key)) {
                throw new NoSuchMethodException("Method " + name + " not found (cached miss)");
            }

            if (handle == null) {
                checkCacheSize();
                Method method = findMethodDeep(owner, name, args);
                if (method == null) {
                    MISSING_MEMBERS.add(key);
                    throw new NoSuchMethodException("Method " + name + " not found in " + owner.getName());
                }

                Class<?> declaringClass = method.getDeclaringClass();

                boolean isPublic = Modifier.isPublic(method.getModifiers()) &&
                        Modifier.isPublic(declaringClass.getModifiers());

                if (!isPublic) {
                    try {
                        method.setAccessible(true);
                    } catch (Exception e) {
                    }
                }

                handle = ROOT_LOOKUP.unreflect(method);
                METHOD_CACHE.put(key, handle);
            }

            MethodHandle invocationHandle;
            if (isStatic) {
                invocationHandle = handle;
            } else {
                invocationHandle = handle.bindTo(targetOrClass);
            }

            
            
            Class<?>[] paramTypes = invocationHandle.type().parameterArray();
            boolean isVarargs = paramTypes.length > 0 && paramTypes[paramTypes.length - 1].isArray();

            Object[] adaptedArgs = adaptArguments(paramTypes, args, isVarargs);
            return invocationHandle.invokeWithArguments(adaptedArgs);
        }

        private static Object[] adaptArguments(Class<?>[] paramTypes, Object[] args, boolean isVarargs) {
            if (!isVarargs) {
                
                Object[] out = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    out[i] = adaptArgument(paramTypes[i], args[i]);
                }
                return out;
            }

            int fixedCount = paramTypes.length - 1;
            Object[] out = new Object[paramTypes.length];

            
            for (int i = 0; i < fixedCount; i++) {
                out[i] = adaptArgument(paramTypes[i], args[i]);
            }

            
            Class<?> arrayType = paramTypes[fixedCount];
            Class<?> componentType = arrayType.getComponentType();

            
            if (args.length == paramTypes.length && arrayType.isInstance(args[fixedCount])) {
                out[fixedCount] = args[fixedCount];
            }
            
            else {
                int varArgsCount = args.length - fixedCount;
                Object varArgsArray = Array.newInstance(componentType, Math.max(0, varArgsCount));

                for (int i = 0; i < varArgsCount; i++) {
                    Object val = adaptArgument(componentType, args[fixedCount + i]);
                    Array.set(varArgsArray, i, val);
                }
                out[fixedCount] = varArgsArray;
            }

            return out;
        }

        static Object getField(Object targetOrClass, String name) throws Throwable {
            boolean isStatic = targetOrClass instanceof Class<?>;
            Class<?> owner = isStatic ? (Class<?>) targetOrClass : targetOrClass.getClass();

            MemberKey key = new MemberKey(owner, name, null);
            MethodHandle handle = FIELD_CACHE.get(key);

            if (handle == null) {
                checkCacheSize();
                Field field = findFieldDeep(owner, name);
                if (field == null) throw new NoSuchFieldException("Field " + name + " not found");

                field.setAccessible(true);
                handle = ROOT_LOOKUP.unreflectGetter(field);
                FIELD_CACHE.put(key, handle);
            }

            return isStatic ? handle.invoke() : handle.invoke(targetOrClass);
        }

        private static Class<?>[] getTypes(Object[] args) {
            Class<?>[] types = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i] == null ? null : args[i].getClass();
            }
            return types;
        }

        static Field findFieldDeep(Class<?> type, String name) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            }
            return null;
        }

        static Method findMethodDeep(Class<?> type, String name, Object[] args) {

            while (type != null && !java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                type = type.getSuperclass();
            }
            if (type == null) return null;
            
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && !m.isVarArgs() && isApplicable(m.getParameterTypes(), args, true)) return m;
            }
            
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && m.isVarArgs() && isVarArgApplicable(m.getParameterTypes(), args, true)) return m;
            }
            
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && !m.isVarArgs() && isApplicable(m.getParameterTypes(), args, false)) return m;
            }
            
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && m.isVarArgs() && isVarArgApplicable(m.getParameterTypes(), args, false)) return m;
            }
            
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name)) {
                        boolean v = m.isVarArgs();
                        if (v ? isVarArgApplicable(m.getParameterTypes(), args, false) : isApplicable(m.getParameterTypes(), args, false)) {
                            return m;
                        }
                    }
                }
            }
            return null;
        }

        static Constructor<?> findConstructorDeep(Class<?> type, Object[] args) {
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (!ctor.isVarArgs() && isApplicable(ctor.getParameterTypes(), args, true)) return ctor;
            }
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (ctor.isVarArgs() && isVarArgApplicable(ctor.getParameterTypes(), args, true)) return ctor;
            }
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (!ctor.isVarArgs() && isApplicable(ctor.getParameterTypes(), args, false)) return ctor;
            }
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (ctor.isVarArgs() && isVarArgApplicable(ctor.getParameterTypes(), args, false)) return ctor;
            }
            return null;
        }

        static boolean isApplicable(Class<?>[] paramTypes, Object[] args, boolean strict) {
            if (paramTypes.length != args.length) return false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isArgApplicable(paramTypes[i], args[i], strict)) return false;
            }
            return true;
        }

        static boolean isVarArgApplicable(Class<?>[] paramTypes, Object[] args, boolean strict) {
            if (args.length < paramTypes.length - 1) return false;
            for (int i = 0; i < paramTypes.length - 1; i++) {
                if (!isArgApplicable(paramTypes[i], args[i], strict)) return false;
            }
            Class<?> varArgComponentType = paramTypes[paramTypes.length - 1].getComponentType();
            for (int i = paramTypes.length - 1; i < args.length; i++) {
                if (!isArgApplicable(varArgComponentType, args[i], strict)) return false;
            }
            return true;
        }

        static boolean isArgApplicable(Class<?> p, Object arg, boolean strict) {
            if (arg == null) return !p.isPrimitive();
            if (arg instanceof Lambda) return p.isInterface();

            if (arg instanceof ObjectTag) {
                return isArgApplicable(p, ((ObjectTag) arg).getJavaObject(), strict);
            }

            if (p.isInstance(arg)) return true;

            if (p.isPrimitive()) {
                Class<?> wrapper = primitiveToWrapper(p);
                if (wrapper.isInstance(arg)) return true;
                if (arg instanceof Number && !strict && Number.class.isAssignableFrom(wrapper)) return true;
            }

            if (arg instanceof String) {
                if (strict) return false;
                if (p == boolean.class || p == Boolean.class) return true;
                if (p == char.class || p == Character.class) return true;
                if (p.isEnum()) return true;
                if (isNumberType(p)) return isParsableNumber((String)arg);
            }
            if (arg instanceof Number && !strict) {
                return isNumberType(p);
            }
            return false;
        }

        private static boolean isNumberType(Class<?> p) {
            Class<?> w = p.isPrimitive() ? primitiveToWrapper(p) : p;
            return Number.class.isAssignableFrom(w);
        }

        private static boolean isParsableNumber(String s) {
            try { Double.parseDouble(s); return true; } catch(Exception e) { return false; }
        }

        public static Object adaptArgument(Class<?> paramType, Object arg) {
            if (arg instanceof Lambda && paramType.isInterface()) {
                return createLambdaProxy(paramType, (Lambda) arg);
            }
            if (arg == null) return null;

            if (arg instanceof ObjectTag) {
                Object javaObj = ((ObjectTag) arg).getJavaObject();
                if (paramType.isInstance(javaObj)) return javaObj;
                return adaptArgument(paramType, javaObj);
            }

            if (arg instanceof String s) {
                String text = s.trim();
                if (paramType == boolean.class || paramType == Boolean.class) return text.equalsIgnoreCase("true") || text.equals("1");
                if (paramType == char.class || paramType == Character.class) return text.isEmpty() ? '\0' : text.charAt(0);
                if (paramType.isEnum()) {
                    try { return Enum.valueOf((Class<Enum>) paramType, text); } catch (Exception ignored) {}
                }
                if (Number.class.isAssignableFrom(primitiveToWrapper(paramType))) return convertToNumber(primitiveToWrapper(paramType), text);
            }

            if (paramType.isPrimitive()) {
                Class<?> wrapper = primitiveToWrapper(paramType);
                if (wrapper.isInstance(arg)) return arg;
                if (arg instanceof Number n) {
                    if (paramType == int.class) return n.intValue();
                    if (paramType == long.class) return n.longValue();
                    if (paramType == double.class) return n.doubleValue();
                    if (paramType == float.class) return n.floatValue();
                    if (paramType == short.class) return n.shortValue();
                    if (paramType == byte.class) return n.byteValue();
                }
            }

            if (Number.class.isAssignableFrom(paramType) && arg instanceof Number n) {
                if (paramType == Integer.class) return n.intValue();
                if (paramType == Long.class) return n.longValue();
                if (paramType == Double.class) return n.doubleValue();
                if (paramType == Float.class) return n.floatValue();
                if (paramType == Short.class) return n.shortValue();
                if (paramType == Byte.class) return n.byteValue();
            }

            return arg;
        }

        private static Object createLambdaProxy(Class<?> interfaceType, Lambda lambda) {
            return Proxy.newProxyInstance(
                    LibraryLoader.getClassLoader(),
                    new Class<?>[]{interfaceType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if (method.getName().equals("toString")) return "LambdaProxy";
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                        }

                        Map<String, Object> localVars = new HashMap<>(lambda.closureContext.locals);

                        if (args != null) {
                            for (int i = 0; i < args.length && i < lambda.paramNames.size(); i++) {
                                localVars.put(lambda.paramNames.get(i), args[i]);
                            }
                        }

                        EvalContext newCtx = new EvalContext(
                                lambda.closureContext.imports,
                                lambda.closureContext.scriptEntry,
                                localVars
                        );

                        Object result = lambda.body.eval(newCtx);
                        return adaptArgument(method.getReturnType(), result);
                    }
            );
        }

        static Class<?> primitiveToWrapper(Class<?> primitive) {
            if (primitive == int.class) return Integer.class;
            if (primitive == long.class) return Long.class;
            if (primitive == double.class) return Double.class;
            if (primitive == float.class) return Float.class;
            if (primitive == short.class) return Short.class;
            if (primitive == byte.class) return Byte.class;
            if (primitive == boolean.class) return Boolean.class;
            if (primitive == char.class) return Character.class;
            return primitive;
        }

        private static Number convertToNumber(Class<?> type, String text) {
            try {
                if (type == Integer.class) return Integer.parseInt(text);
                if (type == Long.class) return Long.parseLong(text);
                if (type == Double.class) return Double.parseDouble(text);
                if (type == Float.class) return Float.parseFloat(text);
                if (type == Short.class) return Short.parseShort(text);
                if (type == Byte.class) return Byte.parseByte(text);
            } catch (Exception ignored) {}
            return null;
        }
    }
}