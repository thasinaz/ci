package com.craftinginterpreters.lox;

class AstPrinter implements Expr.Visitor<String>,
                            Stmt.Visitor<String> {
  String print(Stmt stmt) {
    return stmt.accept(this);
  }

  String print(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitBlockStmt(Stmt.Block stmt) {
    return parenthesize("let ()", stmt.statements.toArray(new Stmt[stmt.statements.size()]));
  }

  @Override
  public String visitBreakStmt(Stmt.Break stmt) {
    return "(break)";
  }

  @Override
  public String visitClassStmt(Stmt.Class stmt) {
    return parenthesize("class " + stmt.name.lexeme, stmt.methods.toArray(new Stmt[stmt.methods.size()]));
  }

  @Override
  public String visitExpressionStmt(Stmt.Expression stmt) {
    return print(stmt.expression);
  }

  @Override
  public String visitFunctionStmt(Stmt.Function stmt) {
    StringBuilder builder = new StringBuilder("define ");
    builder.append(stmt.name.lexeme);
    return parenthesize(builder.toString(), stmt.lambda);
  }

  @Override
  public String visitIfStmt(Stmt.If stmt) {
    if (stmt.elseBranch == null) {
      return parenthesize("if " + print(stmt.condition), stmt.thenBranch);
    } else {
      return parenthesize("if " + print(stmt.condition), stmt.thenBranch, stmt.elseBranch);
    }
  }

  @Override
  public String visitPrintStmt(Stmt.Print stmt) {
    return parenthesize("print", stmt.expression);
  }

  @Override
  public String visitReturnStmt(Stmt.Return stmt) {
    if (stmt.value != null) {
      return parenthesize("return", stmt.value);
    }
    return "(return)";
  }

  @Override
  public String visitVarStmt(Stmt.Var stmt) {
    if (stmt.initializer != null) {
      return parenthesize("define " + stmt.name.lexeme, stmt.initializer);
    }
    return "(define " + stmt.name.lexeme + ")";
  }

  @Override
  public String visitWhileStmt(Stmt.While stmt) {
    return parenthesize("while " + print(stmt.condition), stmt.body);
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    return parenthesize("= " + expr.name.lexeme, expr.value);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitCallExpr(Expr.Call expr) {
    return parenthesize(print(expr.callee), expr.arguments.toArray(new Expr[expr.arguments.size()]));
  }

  @Override
  public String visitGetExpr(Expr.Get expr) {
    StringBuilder builder = new StringBuilder("(get ");

    builder.append(print(expr.object)).append(" ").append(expr.name.lexeme);
    builder.append(")");

    return builder.toString();
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLambdaExpr(Expr.Lambda expr) {
    StringBuilder builder = new StringBuilder("lambda (");
    boolean addSpace = false;
    for (Token param : expr.params) {
      if (addSpace) {
        builder.append(" ");
      }
      builder.append(param.lexeme);
      addSpace = true;
    }
    builder.append(")");

    return parenthesize(builder.toString(), expr.body.toArray(new Stmt[expr.body.size()]));
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    if (expr.value instanceof String) return "\"" + expr.value.toString() + "\"";
    return expr.value.toString();
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitSetExpr(Expr.Set expr) {
    StringBuilder builder = new StringBuilder("(set ");

    builder.append(print(expr.object)).append(" ").append(expr.name.lexeme);
    builder.append(")");

    return builder.toString();
  }

  @Override
  public String visitTernaryExpr(Expr.Ternary expr) {
    return parenthesize(":?", expr.left, expr.middle, expr.right);
  }

  @Override
  public String visitSuperExpr(Expr.Super expr) {
    StringBuilder builder = new StringBuilder("(get super ");

    builder.append(expr.method.lexeme);
    builder.append(")");

    return builder.toString();
  }

  @Override
  public String visitThisExpr(Expr.This expr) {
    return expr.keyword.lexeme;
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  private String parenthesize(String name, Stmt... stmts) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Stmt stmt : stmts) {
      builder.append(" ");
      builder.append(print(stmt));
    }
    builder.append(")");

    return builder.toString();
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(print(expr));
    }
    builder.append(")");

    return builder.toString();
  }
}
