package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction extends LoxLambda {
  private final Stmt.Function declaration;
  private final boolean isInitializer;

  LoxFunction(Stmt.Function declaration, Environment closure,
              boolean isInitializer) {
    super(declaration.lambda, closure);
    this.declaration = declaration;
    this.isInitializer = isInitializer;
  }

  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define(0, instance);
    return new LoxFunction(declaration, environment, isInitializer);
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Object value = super.call(interpreter, arguments);
    if (isInitializer) return closure.getAt(0, 0, null);
    return value;
  }

  @Override
  public String toString() {
    return "<fn " + declaration.name.lexeme + ">";
  }
}
