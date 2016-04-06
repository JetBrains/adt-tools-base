; This is a jasmin file, please compile separately into a class file. Changes made to this file
; have no effect otherwise.
.bytecode 50.0
.source                  VariableBeforeSuper.java
.class                   public com/jasmin/VariableBeforeSuper
.super                   java/lang/Object

.field                   public x I
.field                   public y D

.method                  public <init>(ID)V
   .limit stack          3
   .limit locals         6
   .line                 4
   iconst_3
; Variables are intentionally not sorted, to trigger sorting.
   istore                5
   aload_0
   iconst_2
   istore                4
   invokespecial         java/lang/Object/<init>()V
   .line                 5
   .line                 6
   .line                 7
   aload_0
   iload_1
   iload                 4
   iadd
   iload                 5
   iadd
   putfield              com/jasmin/VariableBeforeSuper/x I
   .line                 8
   aload_0
   dload_2
   putfield              com/jasmin/VariableBeforeSuper/y D
   .line                 9
   return
.end method

