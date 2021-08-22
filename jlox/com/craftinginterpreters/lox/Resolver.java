package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<Token, VariableType>> scopes = new Stack<>();
  private boolean inLoop = false;
  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum VariableType {
    DECLARED,
    INITIALIZING,
    DEFINED,
    USED
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    LAMBDA
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    if (!inLoop) {
      Lox.error(stmt.keyword, "Can't break outside of loop.");
    }

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt.lambda, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      initialize(stmt.name, stmt.initializer);
    }
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    boolean isInLoop = inLoop;
    resolve(stmt.condition);
    inLoop = true;
    resolve(stmt.body);
    inLoop = isInLoop;
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    int index = resolveLocal(expr, expr.name);
    if (index != -1) {
      defineAt(index, expr.name);
    }
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLambdaExpr(Expr.Lambda expr) {
    resolveFunction(expr, FunctionType.LAMBDA);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitTernaryExpr(Expr.Ternary expr) {
    resolve(expr.left);
    resolve(expr.middle);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    int index = resolveLocal(expr, expr.name);
    if (index != -1) {
      Map<Token, VariableType> scope = scopes.get(index);
      VariableType type = scope.get(expr.name);
      if (type == VariableType.DECLARED) {
        Lox.error(expr.name,
            "Can't read uninitialized local variable.");
      } else if (type == VariableType.INITIALIZING) {
        Lox.error(expr.name,
            "Can't read local variable in its own initializer.");
      }
      scope.put(expr.name, VariableType.USED);
    }
    return null;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private int resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name)) {
        int depth = scopes.size() - 1 - i;
        interpreter.resolve(expr, depth);
        return i;
      }
    }

    return -1;
  }

  private void resolveFunction(
      Expr.Lambda lambda, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    for (Token param : lambda.params) {
      declare(param);
      define(param);
    }
    resolve(lambda.body);
    endScope();
    currentFunction = enclosingFunction;
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void beginScope() {
    scopes.push(new HashMap<Token, VariableType>());
  }

  private void endScope() {
    Map<Token, VariableType> scope = scopes.pop();
    for (Map.Entry<Token, VariableType> entry : scope.entrySet()) {
      if (entry.getValue() != VariableType.USED) {
        Lox.error(entry.getKey(),
            "Unused local variable.");
      }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<Token, VariableType> scope = scopes.peek();
    if (scope.containsKey(name)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

    scope.put(name, VariableType.DECLARED);
  }

  private void initialize(Token name, Expr initializer) {
    if (!scopes.isEmpty()) {
      Map<Token, VariableType> scope = scopes.peek();
      scope.put(name, VariableType.INITIALIZING);
    }

    resolve(initializer);
    define(name);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    Map<Token, VariableType> scope = scopes.peek();
    if (scope.get(name) != VariableType.USED) {
      scope.put(name, VariableType.DEFINED);
    }
  }

  private void defineAt(int index, Token name) {
    Map<Token, VariableType> scope = scopes.get(index);
    if (scope.get(name) != VariableType.USED) {
      scope.put(name, VariableType.DEFINED);
    }
  }
}
