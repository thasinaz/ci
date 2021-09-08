package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<Token, VariableStatus>> scopes = new Stack<>();
  private final Stack<Map<Token, Integer>> slots = new Stack<>();
  private final Stack<Integer> slotNo = new Stack<>();
  private boolean inLoop = false;
  private FunctionType currentFunction = FunctionType.NONE;
  private ClassType currentClass = ClassType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum VariableStatus {
    DECLARED,
    INITIALIZING,
    DEFINED,
    USED
  }

  private enum FunctionType {
    NONE,
    LAMBDA,
    FUNCTION,
    INITIALIZER,
    METHOD
  }

  private enum ClassType {
    NONE,
    CLASS
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
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    if (!slotNo.isEmpty()) {
      int slot = nextSlotNo();
      slots.peek().put(stmt.name, slot);
      interpreter.resolve(stmt, slot);
    }

    beginScope();
    Token thisToken = new Token(TokenType.THIS, "this", 0, 0);
    scopes.peek().put(thisToken, VariableStatus.USED);
    slots.peek().put(thisToken, nextSlotNo());

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }

      resolveFunction(method.lambda, declaration);
    }

    endScope();

    currentClass = enclosingClass;
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

    if (!slotNo.isEmpty()) {
      int slot = nextSlotNo();
      slots.peek().put(stmt.name, slot);
      interpreter.resolve(stmt, slot);
    }

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
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Can't return a value from an initializer.");
      }

      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      if (!slotNo.isEmpty()) {
        int slot = nextSlotNo();
        slots.peek().put(stmt.name, slot);
        interpreter.resolve(stmt, slot);
      }
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
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
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
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'this' outside of a class.");
      return null;
    }

    resolveLocal(expr, expr.keyword);
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
      Map<Token, VariableStatus> scope = scopes.get(index);
      if (scope.get(expr.name) == VariableStatus.INITIALIZING) {
        Lox.error(expr.name,
            "Can't read local variable in its own initializer.");
      }
      scope.put(expr.name, VariableStatus.USED);
    }
    return null;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private int resolveLocal(Expr expr, Token name) {
    // System.err.println("find");
    // System.err.println(name);
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Map<Token, VariableStatus> scope = scopes.get(i);
      // System.err.println("get");
      // for (var b : scope.entrySet()) {
        // System.err.println(b);
      // }
      if (scope.containsKey(name)) {
        int depth = scopes.size() - 1 - i;
        int slot;
        if (scope.get(name) == VariableStatus.DECLARED) {
          slot = nextSlotNo(i);
          slots.get(i).put(name, slot);
        } else {
          // for (var b : slots.get(i).entrySet()) {
            // System.err.println(b);
          // }
          // System.err.println(slots);
          slot = slots.get(i).get(name);
        }
        interpreter.resolve(expr, depth, slot);
        return i;
      }
    }

    return -1;
  }

  private void resolveFunction(Expr.Lambda lambda, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    for (Token param : lambda.params) {
      declare(param);
      define(param);
      slots.peek().put(param, nextSlotNo());
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
    scopes.push(new HashMap<Token, VariableStatus>());
    slots.push(new HashMap<Token, Integer>());
    slotNo.push(0);
  }

  private void endScope() {
    Map<Token, VariableStatus> scope = scopes.pop();
    for (Map.Entry<Token, VariableStatus> entry : scope.entrySet()) {
      if (entry.getValue() != VariableStatus.USED) {
        Lox.error(entry.getKey(),
            "Unused local variable.");
      }
    }
    slots.pop();
    slotNo.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<Token, VariableStatus> scope = scopes.peek();
    if (scope.containsKey(name)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

    scope.put(name, VariableStatus.DECLARED);
  }

  private void initialize(Token name, Expr initializer) {
    if (!scopes.isEmpty()) {
      Map<Token, VariableStatus> scope = scopes.peek();
      scope.put(name, VariableStatus.INITIALIZING);
    }

    resolve(initializer);
    define(name);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    Map<Token, VariableStatus> scope = scopes.peek();
    if (scope.get(name) != VariableStatus.USED) {
      scope.put(name, VariableStatus.DEFINED);
    }
  }

  private void defineAt(int index, Token name) {
    Map<Token, VariableStatus> scope = scopes.get(index);
    if (scope.get(name) != VariableStatus.USED) {
      scope.put(name, VariableStatus.DEFINED);
    }
  }

  private int nextSlotNo() {
    int slot = slotNo.pop();
    slotNo.push(slot + 1);
    return slot;
  }

  private int nextSlotNo(int index) {
    int slot = slotNo.get(index);
    slotNo.set(index, slot + 1);
    return slot;
  }
}
