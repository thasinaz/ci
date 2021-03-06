package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
  final Map<String, Object> globals = new HashMap<>();
  private Environment environment = null;
  private final Map<Object, Integer> locals = new HashMap<>();
  private final Map<Object, Integer> slots = new HashMap<>();

  Interpreter() {
    globals.put("clock", new LoxCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });
  }

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  void interpret(Expr expr) {
    try {
      Object value = evaluate(expr);
      System.out.println(stringify(value));
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    throw new Break();
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    Object superclass = null;
    if (stmt.superclass != null) {
      superclass = evaluate(stmt.superclass);
      if (!(superclass instanceof LoxClass)) {
        throw new RuntimeError(stmt.superclass.name,
            "Superclass must be a class.");
      }
    }

    if (environment == null) {
      globals.put(stmt.name.lexeme, null);
    } else {
      environment.define(slots.get(stmt), null);
    }

    if (stmt.superclass != null) {
      environment = new Environment(environment);
      environment.define(0, superclass);
    }

    Map<String, LoxFunction> staticMethods = new HashMap<>();
    for (Stmt.Function method : stmt.staticMethods) {
      LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
      staticMethods.put(method.name.lexeme, function);
    }
    Map<String, LoxFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
      methods.put(method.name.lexeme, function);
    }

    LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, staticMethods, stmt.staticGetters, methods, stmt.getters);
    if (superclass != null) {
      environment = environment.enclosing;
    }

    if (environment == null) {
      globals.put(stmt.name.lexeme, klass);
    } else {
      environment.define(slots.get(stmt), klass);
    }
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function = new LoxFunction(stmt, environment, false);
    if (environment == null) {
      globals.put(stmt.name.lexeme, function);
    } else {
      environment.define(slots.get(stmt), function);
    }
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
      if (environment != null) {
        environment.define(slots.get(stmt), value);
      }
    }
    if (environment == null) {
      globals.put(stmt.name.lexeme, value);
    }
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition))) {
      try {
        execute(stmt.body);
      } catch (Break e) {
        break;
      }
    }
    return null;
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, slots.get(expr), value);
    } else {
      if (globals.containsKey(expr.name.lexeme)) {
        globals.put(expr.name.lexeme, value);
      } else {
        throw new RuntimeError(expr.name,
            "Undeclared variable '" + expr.name.lexeme + "'.");
      }
    }

    return value;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);
      case GREATER:
        if (left instanceof Double && right instanceof Double) {
          return (double)left > (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return ((String)left).compareTo((String)right) > 0;
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case GREATER_EQUAL:
        if (left instanceof Double && right instanceof Double) {
          return (double)left >= (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return ((String)left).compareTo((String)right) >= 0;
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case LESS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left < (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return ((String)left).compareTo((String)right) < 0;
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case LESS_EQUAL:
        if (left instanceof Double && right instanceof Double) {
          return (double)left <= (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return ((String)left).compareTo((String)right) <= 0;
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        }

        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or at least one string.");
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        if ((double)right == 0) {
          throw new RuntimeError(expr.operator, "divide by zero.");
        }
        return (double)left / (double)right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;
      case COMMA:
        return right;
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) {
      arguments.add(evaluate(argument));
    }

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable)callee;
    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }

    return function.call(this, arguments);
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);
    if (object instanceof LoxInstance) {
      return ((LoxInstance) object).get(this, expr.name);
    }

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitLambdaExpr(Expr.Lambda expr) {
    return new LoxLambda(expr, environment);
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitTernaryExpr(Expr.Ternary expr) {
    Object left = evaluate(expr.left);
    Object middle = evaluate(expr.middle);
    Object right = evaluate(expr.right);
    return isTruthy(left) ? middle : right;
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof LoxInstance)) {
      throw new RuntimeError(expr.name,
                             "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((LoxInstance)object).set(expr.name, value);
    return value;
  }

  @Override
  public Object visitSuperExpr(Expr.Super expr) {
    int distance = locals.get(expr);
    LoxClass superclass = (LoxClass)environment.getAt(
        distance, slots.get(expr), expr.keyword);

    LoxInstance object = (LoxInstance)environment.getAt(
        distance - 1, 0, new Token(TokenType.THIS, "this", 0, 0));

    LoxFunction method = superclass.findMethod(expr.method.lexeme);

    if (method == null) {
      throw new RuntimeError(expr.method,
          "Undefined property '" + expr.method.lexeme + "'.");
    }

    return method.bind(object);
  }

  @Override
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);


    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    return lookUpVariable(expr.name, expr);
  }

  void executeBlock(List<Stmt> statements,
                    Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  void resolve(Object node, int depth, int slot) {
    locals.put(node, depth);
    slots.put(node, slot);
  }

  void resolve(Object node, int slot) {
    slots.put(node, slot);
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      return environment.getAt(distance, slots.get(expr), name);
    } else {
      if (globals.containsKey(name.lexeme)) {
        return globals.get(name.lexeme);
      } else {
        throw new RuntimeError(name,
            "Undeclared variable '" + name.lexeme + "'.");
      }
    }
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;

    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
}
