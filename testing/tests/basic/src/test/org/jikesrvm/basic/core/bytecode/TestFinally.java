/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/bytecodeTests/src/TestFloatingRem.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
package test.org.jikesrvm.basic.core.bytecode;

/**
 * @author unascribed
 */
class TestFinally {
  private static int foo() {
    try {
      int a = 1;
      int b = 0;
      return a / b;
    }
    catch (Exception e) {
      return 1;
    }
    finally {
      return 2;
    }
  }

  private static int foo2() {
    try {
      throw new Exception();
    }
    finally {
      return 3;
    }
  }

  public static void main(String args[]) {
    System.out.println("TestFinally.main()");
    System.out.println(TestFinally.foo());
    System.out.println(TestFinally.foo2());
    try {
      System.out.println("hi");      // jsr
      return;
    }
    finally {
      System.out.println("bye");
    }                              // ret
  }
}
