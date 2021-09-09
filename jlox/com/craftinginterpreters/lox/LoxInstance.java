package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class LoxInstance {
  private LoxClass klass;
  private final Map<String, Object> fields = new HashMap<>();

  LoxInstance(LoxClass klass) {
    this.klass = klass;
  }

  Object get(Interpreter interpreter, Token name) {
    if (fields.containsKey(name.lexeme)) {
      return fields.get(name.lexeme);
    }

    if (klass != null) {
      LoxFunction method = klass.findMethod(name.lexeme);
      if (method == null) {
        method = (LoxFunction)klass.get(interpreter, name);
      } else if (klass.isGetter(name.lexeme)) {
        return method.bind(this).call(interpreter, new ArrayList<>());
      }
      if (method != null) return method.bind(this);
    }

    throw new RuntimeError(name,
        "Undefined property '" + name.lexeme + "'.");
  }

  void set(Token name, Object value) {
    fields.put(name.lexeme, value);
  }

 @Override
  public String toString() {
    return klass.name + " instance";
  }
}

