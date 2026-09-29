 package com.craftinginterpreters.lox;

 import java.util.List;

 class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
   private static class BreakException extends RuntimeException {
     private static final long serialVersionUID = 1L;
   }

   final Environment globals = new Environment();
   private Environment environment = globals;
   private boolean replMode = false;

   void interpret(List<Stmt> statements, boolean replMode) {
     this.replMode = replMode;
     try {
       for (Stmt statement : statements) {
         if (statement != null) execute(statement);
       }
     } catch (RuntimeError error) {
       Lox.runtimeError(error);
     } finally {
       this.replMode = false;
     }
   }

   private void execute(Stmt stmt) {
     stmt.accept(this);
   }

   void executeBlock(List<Stmt> statements, Environment blockEnvironment) {
     Environment previous = environment;
     try {
       environment = blockEnvironment;
       for (Stmt statement : statements) {
         if (statement != null) execute(statement);
       }
     } finally {
       environment = previous;
     }
   }

   @Override
   public Void visitBlockStmt(Stmt.Block stmt) {
     executeBlock(stmt.statements, new Environment(environment));
     return null;
   }

   @Override
   public Void visitBreakStmt(Stmt.Break stmt) {
     throw new BreakException();
   }

   @Override
   public Void visitExpressionStmt(Stmt.Expression stmt) {
     Object value = evaluate(stmt.expression);
     if (replMode) System.out.println(stringify(value));
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
     System.out.println(stringify(evaluate(stmt.expression)));
     return null;
   }

   @Override
   public Void visitVarStmt(Stmt.Var stmt) {
     if (stmt.initializer == null) {
       environment.defineUninitialized(stmt.name.lexeme);
     } else {
       environment.define(stmt.name.lexeme, evaluate(stmt.initializer));
     }
     return null;
   }

   @Override
   public Void visitWhileStmt(Stmt.While stmt) {
     try {
       while (isTruthy(evaluate(stmt.condition))) {
         execute(stmt.body);
       }
     } catch (BreakException breakException) {
       // Exit only the nearest loop.
     }
     return null;
   }

   @Override
   public Object visitAssignExpr(Expr.Assign expr) {
     Object value = evaluate(expr.value);
     environment.assign(expr.name, value);
     return value;
   }

   @Override
   public Object visitVariableExpr(Expr.Variable expr) {
     return environment.get(expr.name);
   }

   @Override
   public Object visitLiteralExpr(Expr.Literal expr) {
     return expr.value;
   }

   @Override
   public Object visitGroupingExpr(Expr.Grouping expr) {
     return evaluate(expr.expression);
   }

   @Override
   public Object visitUnaryExpr(Expr.Unary expr) {
     Object right = evaluate(expr.right);
     switch (expr.operator.type) {
       case BANG:
         return !isTruthy(right);
       case MINUS:
         checkNumberOperand(expr.operator, right);
         return -(double) right;
       default:
         return null;
     }
   }

   @Override
   public Object visitBinaryExpr(Expr.Binary expr) {
     Object left = evaluate(expr.left);

     switch (expr.operator.type) {
       case AND:
         if (!isTruthy(left)) return left;
         return evaluate(expr.right);
       case OR:
         if (isTruthy(left)) return left;
         return evaluate(expr.right);
       case COMMA:
         return evaluate(expr.right);
       case GREATER:
         return compare(expr.operator, left, evaluate(expr.right)) > 0;
       case GREATER_EQUAL:
         return compare(expr.operator, left, evaluate(expr.right)) >= 0;
       case LESS:
         return compare(expr.operator, left, evaluate(expr.right)) < 0;
       case LESS_EQUAL:
         return compare(expr.operator, left, evaluate(expr.right)) <= 0;
       case MINUS:
         return numericBinary(expr, left, evaluate(expr.right), '-');
       case PLUS: {
         Object right = evaluate(expr.right);
         if (left instanceof Double && right instanceof Double) {
           return (double) left + (double) right;
         }
         if (left instanceof String || right instanceof String) {
           return stringify(left) + stringify(right);
         }
         throw new RuntimeError(expr.operator,
             "Operands must be two numbers or include a string.");
       }
       case SLASH: {
         Object right = evaluate(expr.right);
         checkNumberOperands(expr.operator, left, right);
         if ((double) right == 0.0) {
           throw new RuntimeError(expr.operator, "Cannot divide by zero.");
         }
         return (double) left / (double) right;
       }
       case STAR:
         return numericBinary(expr, left, evaluate(expr.right), '*');
       case BANG_EQUAL:
         return !isEqual(left, evaluate(expr.right));
       case EQUAL_EQUAL:
         return isEqual(left, evaluate(expr.right));
       default:
         return null;
     }
   }

   private Object numericBinary(Expr.Binary expr, Object left, Object right, char operator) {
     checkNumberOperands(expr.operator, left, right);
     if (operator == '-') return (double) left - (double) right;
     return (double) left * (double) right;
   }

   @Override
   public Object visitConditionalExpr(Expr.Conditional expr) {
     if (isTruthy(evaluate(expr.condition))) return evaluate(expr.thenBranch);
     return evaluate(expr.elseBranch);
   }

   private Object evaluate(Expr expr) {
     return expr.accept(this);
   }

   private boolean isTruthy(Object object) {
     if (object == null) return false;
     if (object instanceof Boolean) return (boolean) object;
     return true;
   }

   private boolean isEqual(Object a, Object b) {
     if (a == null && b == null) return true;
     if (a == null) return false;
     return a.equals(b);
   }

   private int compare(Token operator, Object left, Object right) {
     if (left instanceof Double && right instanceof Double) {
       return Double.compare((double) left, (double) right);
     }
     if (left instanceof String && right instanceof String) {
       return ((String) left).compareTo((String) right);
     }
     throw new RuntimeError(operator,
         "Operands must be two numbers or two strings.");
   }

   private void checkNumberOperand(Token operator, Object operand) {
     if (operand instanceof Double) return;
     throw new RuntimeError(operator, "Operand must be a number.");
   }

   private void checkNumberOperands(Token operator, Object left, Object right) {
     if (left instanceof Double && right instanceof Double) return;
     throw new RuntimeError(operator, "Operands must be numbers.");
   }

   private String stringify(Object object) {
     if (object == null) return "nil";
     if (object instanceof Double) {
       String text = object.toString();
       if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
       return text;
     }
     return object.toString();
   }
 }

