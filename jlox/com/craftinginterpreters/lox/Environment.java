package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
  final Environment enclosing;
  private final Map<String, Object[]> values = new HashMap<>();

  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  private void define(String name, boolean initialized, Object value) {
    values.put(name, new Object[]{initialized, value});
  }

  void define(String name, Object value) {
    define(name, true, value);
  }

  void define(String name) {
    define(name, false, null);
  }

  Object get(Token name) {
    if (values.containsKey(name.lexeme)) {
      if ((boolean)values.get(name.lexeme)[0]) {
        return values.get(name.lexeme)[1];
      }

      throw new RuntimeError(name,
          "Uninitialized variable '" + name.lexeme + "'.");
    }

    if (enclosing != null) return enclosing.get(name);

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  Object getAt(int distance, String name) {
    return ancestor(distance).values.get(name);
  }

  void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, new Object[]{true, value});
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  void assignAt(int distance, Token name, Object value) {
    ancestor(distance).values.put(name.lexeme, value);
  }

  Environment ancestor(int distance) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }
}
