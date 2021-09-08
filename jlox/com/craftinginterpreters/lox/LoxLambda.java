package com.craftinginterpreters.lox;

import java.util.List;

class LoxLambda implements LoxCallable {
  private static int nextId = 0;
  private final int id;
  private final Expr.Lambda lambda;
  final Environment closure;

  LoxLambda(Expr.Lambda lambda, Environment closure) {
    this.closure = closure;
    this.id = nextId++;
    this.lambda = lambda;
  }

  @Override
  public int arity() {
    return lambda.params.size();
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < lambda.params.size(); i++) {
      environment.define(i, arguments.get(i));
    }

    try {
      interpreter.executeBlock(lambda.body, environment);
    } catch (Return returnValue) {
      return returnValue.value;
    }
    return null;
  }

  @Override
  public String toString() {
    return "<lambda " + id + ">";
  }
}
