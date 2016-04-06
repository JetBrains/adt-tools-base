;  This is a jasmin file, please compile separately into a class file. Changes made to this file
; have no effect  otherwise.
.bytecode 50.0
.source                  VariableBeforeSuper.java
.class                   public com/jasmin/VariableBeforeSuper
.super                   java/lang/Object

.field                   public x I
.field                   public y D

.method                  public <init>(ID)V
   .limit stack          3
   .limit locals         7
   .line                 4
   iconst_3
   istore                5                              ; 5=3
   aload_0
; Change the value stored in 4
   bipush                39
   istore                4                              ; 4=39 5=3
; Create a new variable 6
   bipush                10
   istore                6                              ; 4=39 5=3 6=10
; Add them up and restore on 5
   iload                 5
   iload                 6
   iadd
   istore                5                              ; 4=39 5=13 6=10
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
   putfield              com/jasmin/VariableBeforeSuper/x I         ; 1 + 4=39 5=13 == 53
   .line                 8
   aload_0
   dload_2
   putfield              com/jasmin/VariableBeforeSuper/y D
   .line                 9
   return
.end method

