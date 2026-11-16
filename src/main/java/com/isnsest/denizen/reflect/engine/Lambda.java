package com.isnsest.denizen.reflect.engine;

import com.isnsest.denizen.reflect.engine.ast.Node;

import java.util.List;

public record Lambda(List<String> paramNames, Node body, EvalContext closureContext) {}
