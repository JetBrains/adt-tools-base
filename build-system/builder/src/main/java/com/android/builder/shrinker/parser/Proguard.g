grammar Proguard;

options{
  k = 3;
}

tokens {
  NEGATOR = '!';
}

@header {
package com.android.builder.shrinker.parser;
import com.android.builder.shrinker.parser.ClassTypeSpecification.TypeEnum;
}

@lexer::header {
package com.android.builder.shrinker.parser;
}

prog [Flags flags, String baseDirectory]
  :
  (
    ('-basedirectory' baseDir=NAME {baseDirectory=$baseDir.text;})
    | ('-include'|'@') proguardFile=NAME {GrammarActions.parse($proguardFile.text, baseDirectory, $flags);}
    | ('-keepclassmembers' keepModifier=keepOptionModifier? classSpec=classSpecification {GrammarActions.addKeepClassMembers($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | ('-keepclasseswithmembers' keepModifier=keepOptionModifier? classSpec=classSpecification {GrammarActions.addKeepClassesWithMembers($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | ('-keep' keepModifier=keepOptionModifier? classSpec=classSpecification {GrammarActions.addKeepClassSpecification($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | ('-dontshrink' {$flags.setShrink(false);})
    | ('-dontoptimize'  {$flags.setOptimize(false);})
    | ('-dontpreverify'  {$flags.setPreverify(false);})
    | ('-dontobfuscate' {$flags.setObfuscate(false);})
    | unFlag=unsupportedFlag {GrammarActions.unsupportedFlag($unFlag.text);}
  )*
  EOF
  ;
  catch [RecognitionException e] {
    throw e;
  }

private unsupportedFlag
  :
  ('-skipnonpubliclibraryclasses'
    | '-dontskipnonpubliclibraryclasses'
    | '-dontskipnonpubliclibraryclassmembers'
    | ('-keepdirectories' {FilterSpecification directory_filter = new FilterSpecification();} filter[directory_filter])
    | ('-target' NAME) //version
    | '-forceprocessing'
    | ('-printusage' NAME) //[filename]
    | ('-whyareyoukeeping' classSpecification)
    | ('-optimizations' {FilterSpecification optimization_filter = new FilterSpecification();} filter[optimization_filter])
    | ('-optimizationpasses' NAME) //n
    | ('-assumenosideeffects' classSpecification)
    | '-allowaccessmodification'
    | '-mergeinterfacesaggressively'
    | '-overloadaggressively'
    | '-microedition'
    | '-verbose'
    | ('-dontnote' {FilterSpecification class_filter = new FilterSpecification();} filter[class_filter])
    | ('-dontwarn' {FilterSpecification class_filter = new FilterSpecification();} filter[class_filter])
    | ('-renamesourcefileattribute' sourceFile=NAME?)
    | '-ignorewarnings'
    | ('-printconfiguration' NAME?) //[filename]
    | ('-dump' NAME?) //[filename]
    | ('-adaptclassstrings' {FilterSpecification filter = new FilterSpecification();} filter[filter])
    | ('-applymapping' mapping=NAME )
    | ('-keepattributes' {FilterSpecification attribute_filter = new FilterSpecification();} filter[attribute_filter] )
    | '-keepparameternames'
    | '-obfuscationdictionary' obfuscationDictionary=NAME
    | '-classobfuscationdictionary' classObfuscationDictionary=NAME
    | '-packageobfuscationdictionary' packageObfuscationDictionary=NAME
    | '-printmapping' outputMapping=NAME?
    | ('-keeppackagenames' {FilterSpecification package_filter = new FilterSpecification();} filter[package_filter] )
    | ('-repackageclasses' ('\'' newPackage=NAME? '\'')? )
    | ('-flattenpackagehierarchy' ('\'' newPackage=NAME? '\'')? )
    | '-dontusemixedcaseclassnames'
    | '-useuniqueclassmembernames'
    | ('-printseeds' seedOutputFile=NAME? )
    | ('-adaptresourcefilenames' {FilterSpecification file_filter = new FilterSpecification();} filter[file_filter] )
    | ('-adaptresourcefilecontents' {FilterSpecification file_filter = new FilterSpecification();} filter[file_filter] )
    | '-injars' inJars=classpath
    | '-outjars' outJars=classpath
    | '-libraryjars' libraryJars=classpath
    | ('-keepclassmembernames' classSpec=classSpecification  )
    | ('-keepclasseswithmembernames' classSpec=classSpecification  )
    | ('-keepnames' classSpec=classSpecification )
  )
  ;

private classpath
  :  NAME ((':'|';') classpath)?
  ;

private filter [FilterSpecification filter]
  :
  nonEmptytFilter[filter]
  | {GrammarActions.filter($filter, false, "**");}
  ;


private nonEmptytFilter [FilterSpecification filter]
@init {
  boolean negator = false;
}
  :
  ((NEGATOR {negator=true;})? NAME {GrammarActions.filter($filter, negator, $NAME.text);} (',' nonEmptytFilter[filter])?)
  ;

private classSpecification returns [ClassSpecification classSpec]
@init{
  ModifierSpecification modifier = new ModifierSpecification();
  boolean hasNameNegator = false;
}
  :
  (annotation)?
  cType=classModifierAndType[modifier]
  (NEGATOR {hasNameNegator = true;})? NAME {classSpec = GrammarActions.classSpec($NAME.text, hasNameNegator, cType, $annotation.annotSpec, modifier);}
  (inheritanceSpec=inheritance {classSpec.setInheritance(inheritanceSpec);})?
  members[classSpec]?
  ;

private classModifierAndType[ModifierSpecification modifier] returns [ClassTypeSpecification cType]
@init{
  boolean hasNegator = false;
}
  :
  (NEGATOR {hasNegator = true;})?
  (
  'public' {GrammarActions.addModifier(modifier, Modifier.PUBLIC, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | 'abstract' {GrammarActions.addModifier(modifier, Modifier.ABSTRACT, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | 'final' {GrammarActions.addModifier(modifier, Modifier.FINAL, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | classType {cType=GrammarActions.classType($classType.type, hasNegator); }
  )
  ;

private classType returns [TypeEnum type]
  :
    'interface' {$type = TypeEnum.INTERFACE;}
  | 'enum' {$type = TypeEnum.ENUM;}
  | 'class' {$type = TypeEnum.CLASS;}
  ;

private members [ClassSpecification classSpec]
  :
  '{'
    member[classSpec]*
  '}'
  ;

private member [ClassSpecification classSpec]
  :
    annotation? modifiers
    (
      (typeSig=type)? name=(NAME|'<init>') (signature=arguments {GrammarActions.method(classSpec, $annotation.annotSpec, typeSig, $name.text, signature, $modifiers.modifiers);}
                  | {GrammarActions.fieldOrAnyMember(classSpec, $annotation.annotSpec, typeSig, $name.text, $modifiers.modifiers);})
      | '<methods>' {GrammarActions.method(classSpec, $annotation.annotSpec,
          GrammarActions.getSignature("***", 0), "*", "("+ GrammarActions.getSignature("...", 0) + ")",
          $modifiers.modifiers);}
      | '<fields>' {GrammarActions.field(classSpec, $annotation.annotSpec, null, "*", $modifiers.modifiers);}
    ) ';'
  ;

private annotation returns [AnnotationSpecification annotSpec]
@init{
  boolean hasNameNegator = false;
}
  :  '@' (NEGATOR {hasNameNegator = true;})? NAME {$annotSpec = GrammarActions.annotation($NAME.text, hasNameNegator);};

private modifiers returns [ModifierSpecification modifiers]
@init{
  modifiers = new ModifierSpecification();
}
  :
  modifier[modifiers]*
  ;

private modifier [ModifierSpecification modifiers]
@init{
  boolean hasNegator = false;
}
  :
  (NEGATOR {hasNegator = true;})?
  (
    'public' {modifiers.addModifier(Modifier.PUBLIC, hasNegator);}
    | 'private' {modifiers.addModifier(Modifier.PRIVATE, hasNegator);}
    | 'protected' {modifiers.addModifier(Modifier.PROTECTED, hasNegator);}
    | 'static' {modifiers.addModifier(Modifier.STATIC, hasNegator);}
    | 'synchronized' {modifiers.addModifier(Modifier.SYNCHRONIZED, hasNegator);}
    | 'volatile' {modifiers.addModifier(Modifier.VOLATILE, hasNegator);}
    | 'native' {modifiers.addModifier(Modifier.NATIVE, hasNegator);}
    | 'abstract' {modifiers.addModifier(Modifier.ABSTRACT, hasNegator);}
    | 'strictfp' {modifiers.addModifier(Modifier.STRICTFP, hasNegator);}
    | 'final' {modifiers.addModifier(Modifier.FINAL, hasNegator);}
    | 'transient' {modifiers.addModifier(Modifier.TRANSIENT, hasNegator);}
    | 'synthetic' {modifiers.addModifier(Modifier.SYNTHETIC, hasNegator);}
    | 'bridge' {modifiers.addModifier(Modifier.BRIDGE, hasNegator);}
    | 'varargs' {modifiers.addModifier(Modifier.VARARGS, hasNegator);}
  )
  ;

private inheritance returns [InheritanceSpecification inheritanceSpec]
@init{
  boolean hasNameNegator = false;
}
  :
  ('extends' | 'implements')
  annotation? (NEGATOR {hasNameNegator = true;})? NAME {inheritanceSpec = GrammarActions.createInheritance($NAME.text, hasNameNegator, $annotation.annotSpec);};

private arguments returns [String signature]
  :
  '(' {signature = "(";}
    (
      (
        parameterSig=type {signature += parameterSig;}
        (',' parameterSig=type {signature += parameterSig;})*
        )?
      )
    ')' {signature += ")";}
  ;

private type returns [String signature]
@init {
  int dim = 0;
}
  :
  (
    typeName='%' {String sig = $typeName.text; signature = GrammarActions.getSignature(sig == null ? "" : sig, 0);}
    |
    (typeName=NAME ('[]' {dim++;})*  {String sig = $typeName.text; signature = GrammarActions.getSignature(sig == null ? "" : sig, dim);})
  )
  ;

private keepOptionModifier returns [KeepModifier modifier]
  : ','
  ('allowshrinking' {modifier = KeepModifier.ALLOW_SHRINKING;}
  | 'allowoptimization' // Optimizations not supported
  | 'allowobfuscation' {modifier = KeepModifier.ALLOW_OBFUSCATION;})
  ;

private NAME  : ('a'..'z'|'A'..'Z'|'_'|'0'..'9'|'?'|'$'|'.'|'*'|'/'|'\\'|'-'|'<'|'>')+ ;

LINE_COMMENT
  :  '#' ~( '\r' | '\n' )* {$channel=HIDDEN;}
  ;

private WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
        ) {$channel=HIDDEN;}
    ;


