/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.selector.filter;

/**
 * An expression which performs an operation on two expression values
 */
public abstract class ArithmeticExpression extends BinaryExpression {

   protected static final int INTEGER = 1;
   protected static final int LONG = 2;
   protected static final int DOUBLE = 3;
   boolean convertStringExpressions = false;

   public ArithmeticExpression(Expression left, Expression right) {
      super(left, right);
      convertStringExpressions = ComparisonExpression.CONVERT_STRING_EXPRESSIONS.get() != null;
   }

   public static Expression createPlus(Expression left, Expression right) {
      return new ArithmeticExpression(left, right) {
         @Override
         protected Object evaluate(Object lvalue, Object rvalue) {
            if (lvalue instanceof String text) {
               return text + rvalue;
            } else {
               return plus(asNumber(lvalue), asNumber(rvalue));
            }
         }

         @Override
         public String getExpressionSymbol() {
            return "+";
         }
      };
   }

   public static Expression createMinus(Expression left, Expression right) {
      return new ArithmeticExpression(left, right) {
         @Override
         protected Object evaluate(Object lvalue, Object rvalue) {
            return minus(asNumber(lvalue), asNumber(rvalue));
         }

         @Override
         public String getExpressionSymbol() {
            return "-";
         }
      };
   }

   public static Expression createMultiply(Expression left, Expression right) {
      return new ArithmeticExpression(left, right) {

         @Override
         protected Object evaluate(Object lvalue, Object rvalue) {
            return multiply(asNumber(lvalue), asNumber(rvalue));
         }

         @Override
         public String getExpressionSymbol() {
            return "*";
         }
      };
   }

   public static Expression createDivide(Expression left, Expression right) {
      return new ArithmeticExpression(left, right) {

         @Override
         protected Object evaluate(Object lvalue, Object rvalue) {
            return divide(asNumber(lvalue), asNumber(rvalue));
         }

         @Override
         public String getExpressionSymbol() {
            return "/";
         }
      };
   }

   public static Expression createMod(Expression left, Expression right) {
      return new ArithmeticExpression(left, right) {

         @Override
         protected Object evaluate(Object lvalue, Object rvalue) {
            return mod(asNumber(lvalue), asNumber(rvalue));
         }

         @Override
         public String getExpressionSymbol() {
            return "%";
         }
      };
   }

   protected Number plus(Number left, Number right) {
      return switch (numberType(left, right)) {
         case INTEGER -> left.intValue() + right.intValue();
         case LONG -> left.longValue() + right.longValue();
         default -> left.doubleValue() + right.doubleValue();
      };
   }

   protected Number minus(Number left, Number right) {
      return switch (numberType(left, right)) {
         case INTEGER -> left.intValue() - right.intValue();
         case LONG -> left.longValue() - right.longValue();
         default -> left.doubleValue() - right.doubleValue();
      };
   }

   protected Number multiply(Number left, Number right) {
      return switch (numberType(left, right)) {
         case INTEGER -> left.intValue() * right.intValue();
         case LONG -> left.longValue() * right.longValue();
         default -> left.doubleValue() * right.doubleValue();
      };
   }

   protected Number divide(Number left, Number right) {
      return left.doubleValue() / right.doubleValue();
   }

   protected Number mod(Number left, Number right) {
      return left.doubleValue() % right.doubleValue();
   }

   private int numberType(Number left, Number right) {
      if (isDouble(left) || isDouble(right)) {
         return DOUBLE;
      } else if (left instanceof Long || right instanceof Long) {
         return LONG;
      } else {
         return INTEGER;
      }
   }

   private boolean isDouble(Number n) {
      return n instanceof Float || n instanceof Double;
   }

   protected Number asNumber(Object value) {
      if (value instanceof Number number) {
         return number;
      } else {
         if (convertStringExpressions && value instanceof String string) {
            try {
               if (string.contains(".")) {
                  return Double.valueOf(string);
               } else {
                  return Long.valueOf(string);
               }
            } catch (NumberFormatException e) {
               throw new RuntimeException("Cannot convert value: " + value + " into a number");
            }
         }
         throw new RuntimeException("Cannot convert value: " + value + " into a number");
      }
   }

   @Override
   public Object evaluate(Filterable message) throws FilterException {
      Object lvalue = left.evaluate(message);
      if (lvalue == null) {
         return null;
      }
      Object rvalue = right.evaluate(message);
      if (rvalue == null) {
         return null;
      }
      return evaluate(lvalue, rvalue);
   }

   protected abstract Object evaluate(Object lvalue, Object rvalue);

}
