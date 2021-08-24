package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class Environment {
  final Environment enclosing;
  private final List<VariableInfo> values = new ArrayList<>();

  private class VariableInfo {
    private Object value = null;

    VariableInfo(Object value) {
      this.value = value;
    }
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  void define(int slot, Object value) {
    while (values.size() <= slot) {
      values.add(null);
    }
    values.set(slot, new VariableInfo(value));
  }

  Object get(int slot, Token name) {
    if (slot < values.size()) {
      VariableInfo variable = values.get(slot);
      if (variable != null) {
        return variable.value;
      }
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  Object getAt(int distance, int slot, Token name) {
    return ancestor(distance).get(slot, name);
  }

  void assign(int slot, Token name, Object value) {
    while (values.size() <= slot) {
      values.add(null);
    }
    values.set(slot, new VariableInfo(value));
  }

  void assignAt(int distance, int slot, Token name, Object value) {
    ancestor(distance).assign(slot, name, value);
  }

  Environment ancestor(int distance) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }
}
