package com.craftinginterpreters.lox;

class LoxFunction extends LoxLambda {
  private final Token name;

  LoxFunction(Stmt.Function declaration, Environment closure) {
    super(declaration.lambda, closure);
    name = declaration.name;
  }

  @Override
  public String toString() {
    return "<fn " + name.lexeme + ">";
  }
}
