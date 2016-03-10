package com.stefandekanski.weakwrap.processor;


import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import com.stefandekanski.weakwrap.anotation.WeakWrap;
import org.junit.Before;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.Arrays;
import java.util.Set;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class WeakWrapProcessorTest {

    WeakWrapProcessor weakWrapProcessor;

    @Before
    public void setUp() {
        weakWrapProcessor = new WeakWrapProcessor();
    }

    @Test
    public void testSupportedAnnotationTypes() {
        Set<String> supportedAnnotationTypes = weakWrapProcessor.getSupportedAnnotationTypes();

        assertThat(supportedAnnotationTypes.size(), is(1));
        assertThat(supportedAnnotationTypes, hasItem(WeakWrap.class.getCanonicalName()));
    }

    @Test
    public void testAnnotationOnMethod() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.AnnotationOnMethod", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "public class AnnotationOnMethod {",
                "   @WeakWrap",
                "   public void someMethod(){",
                "   }",
                "}"));

        assertAbout(javaSource()).that(source).failsToCompile();
    }

    @Test
    public void testAnnotationOnField() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.AnnotationOnMethod", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "public class AnnotationOnMethod {",
                "   @WeakWrap",
                "   private final int mockInt;",
                "}"));

        assertAbout(javaSource()).that(source).failsToCompile();
    }

    @Test
    public void testDeepPackage() {
        JavaFileObject source = JavaFileObjects.forSourceString("com.very.deep.something.SomeInterface", Joiner.on('\n').join(
                "package com.very.deep.something;",
                importObjectMethodStuff(),
                importWeakWrapAnnotation(),
                "public interface SomeInterface {",
                "    @WeakWrap",
                "    interface View {",
                "    }",
                "}"
        ));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("com.very.deep.something.WeakWrapSomeInterfaceView", Joiner.on('\n').join(
                "package com.very.deep.something;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapInterfaceStart("SomeInterface.View"),
                objectOverriddenMethods("SomeInterface.View"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testAnnotationOnNonStaticInnerClass() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.AnnotationOnNonStaticInnerClass", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakWrapAnnotation(),
                "public class AnnotationOnNonStaticInnerClass {",
                "   @WeakWrap",
                "   public class NonStaticInner{",
                "   }",
                "}"));

        assertAbout(javaSource())
                .that(source)
                .processedWith(weakWrapProcessor)
                .failsToCompile()
                .withErrorContaining(WeakWrapWriter.TYPE_VALIDATION_MSG);
    }

    @Test
    public void testEmptyClass() {
        JavaFileObject source = JavaFileObjects.forSourceString("EmptyClass", Joiner.on('\n').join(
                importWeakWrapAnnotation(),
                importObjectMethodStuff(),
                "@WeakWrap",
                "public class EmptyClass {",
                "    protected Object clone(){",
                "        return null;",
                "    }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("WeakWrapEmptyClass", Joiner.on('\n').join(
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("EmptyClass"),
                objectOverriddenMethods("EmptyClass"),

                "protected Object clone(){",
                wrapperMethodBodyAndClose("EmptyClass", "clone()", "null"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testSimpleInterface() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleInterface", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "@WeakWrap",
                "interface SimpleInterface {",
                "   void someMethod();",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleInterface", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapInterfaceStart("SimpleInterface"),
                objectOverriddenMethods("SimpleInterface"),

                "public void someMethod(){",
                wrapperMethodBodyAndClose("SimpleInterface", "someMethod()"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testClassModifiers() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.ModifiersClass", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "@WeakWrap",
                "abstract class ModifiersClass {",
                "   private void somePrivateMethod() {",
                "   }",
                "   public static void someStaticMethod(){",
                "   }",
                "   void defaultMethod(){",
                "   }",
                "   public abstract void someAbstractMethod();",
                "   protected void someProtectedMethod(){",
                "   }",
                "   protected abstract void protectedAbstractMethod();",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapModifiersClass", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("ModifiersClass"),
                objectOverriddenMethods("ModifiersClass"),

                "void defaultMethod(){",
                wrapperMethodBodyAndClose("ModifiersClass", "defaultMethod()"),

                "public void someAbstractMethod(){",
                wrapperMethodBodyAndClose("ModifiersClass", "someAbstractMethod()"),

                "protected void someProtectedMethod(){",
                wrapperMethodBodyAndClose("ModifiersClass", "someProtectedMethod()"),

                "protected void protectedAbstractMethod(){",
                wrapperMethodBodyAndClose("ModifiersClass", "protectedAbstractMethod()"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testClassWithMethodsThatThrowExceptions() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.ClassWithMethodsThatThrowExceptions", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakWrapAnnotation(),
                "import java.io.IOException;",
                "@WeakWrap",
                "public class ClassWithMethodsThatThrowExceptions {",
                "   public void throwMethod() throws IOException,InterruptedException{",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapClassWithMethodsThatThrowExceptions", Joiner.on('\n').join(
                "package test;",
                "import java.io.IOException;",
                "import java.lang.InterruptedException;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("ClassWithMethodsThatThrowExceptions"),
                objectOverriddenMethods("ClassWithMethodsThatThrowExceptions"),

                "public void throwMethod() throws IOException,InterruptedException{",
                wrapperMethodBodyAndClose("ClassWithMethodsThatThrowExceptions", "throwMethod()"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testClassWithVoidMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleClass", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "@WeakWrap",
                "public class SimpleClass {",
                "   public void simpleMethod(){",
                "   }",
                "   public void simpleParam(int par){",
                "   }",
                "   public void simpleMultipleParam(int in,long lo,double dou){",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleClass", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("SimpleClass"),
                objectOverriddenMethods("SimpleClass"),

                "public void simpleMethod(){",
                wrapperMethodBodyAndClose("SimpleClass", "simpleMethod()"),

                "public void simpleParam(int par){",
                wrapperMethodBodyAndClose("SimpleClass", "simpleParam(par)"),

                "public void simpleMultipleParam(int in,long lo,double dou){",
                wrapperMethodBodyAndClose("SimpleClass", "simpleMultipleParam(in, lo, dou)"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testClassWithVarargMethod() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.ClassWithVarargs", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "@WeakWrap",
                "public class ClassWithVarargs {",
                "   public void varargMethod(int a,Object... objectVararg){",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapClassClassWithVarargs", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("ClassWithVarargs"),
                objectOverriddenMethods("ClassWithVarargs"),

                "public void varargMethod(int a,Object... objectVararg){",
                wrapperMethodBodyAndClose("ClassWithVarargs", "varargMethod(a,objectVararg)"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testInnerClassAndInterface() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleClass", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "public class SimpleClass {",
                "   @WeakWrap",
                "   public static class InnerClass {",
                "   }",
                "   @WeakWrap",
                "   interface InnerInterface{",
                "   }",
                "}"));

        JavaFileObject expectedClassSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleClassInnerClass", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("SimpleClass.InnerClass"),
                objectOverriddenMethods("SimpleClass.InnerClass"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        JavaFileObject expectedInterfaceSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleClassInnerInterface", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapInterfaceStart("SimpleClass.InnerInterface"),
                objectOverriddenMethods("SimpleClass.InnerInterface"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedClassSource, expectedInterfaceSource);
    }

    @Test
    public void testClassWithGenericMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleGenericMethods", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "import java.util.Collection;",
                "import java.util.List;",
                "@WeakWrap",
                "public class SimpleGenericMethods {",
                "   public void simpleGenericParam(List<Long> longList){",
                "   }",
                "   public void simpleWildCard(Collection<? extends Number > col){",
                "   }",
                "   public <K, V> void simpleGenericMethod(K key, V val) {",
                "   }",
                "   public <T extends Comparable<T>> void genericMethod(T[] anArray, T elem) {",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleGenericMethods", Joiner.on('\n').join(
                "package test;",
                "import java.lang.Comparable;",
                "import java.lang.Long;",
                "import java.lang.Number;",
                importObjectMethodStuff(),
                importWeakReference(),
                "import java.util.Collection;",
                "import java.util.List;",

                wrapClassStart("SimpleGenericMethods"),
                objectOverriddenMethods("SimpleGenericMethods"),

                "public void simpleGenericParam(List<Long> longList){",
                wrapperMethodBodyAndClose("SimpleGenericMethods", "simpleGenericParam(longList)"),

                "public void simpleWildCard(Collection<? extends Number> col){",
                wrapperMethodBodyAndClose("SimpleGenericMethods", "simpleWildCard(col)"),

                "public <K, V> void simpleGenericMethod(K key, V val) {",
                wrapperMethodBodyAndClose("SimpleGenericMethods", "simpleGenericMethod(key,val)"),

                "public <T extends Comparable<T>> void genericMethod(T[] anArray, T elem){",
                wrapperMethodBodyAndClose("SimpleGenericMethods", "genericMethod(anArray,elem)"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testClassWithReturnTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimplePrimitiveClass", Joiner.on('\n').join(
                "package test;",
                importWeakWrapAnnotation(),
                "@WeakWrap",
                "public class SimplePrimitiveClass {",
                "   public byte primitiveByteMethod(){",
                "       return 0;",
                "   }",
                "   public short primitiveShortMethod(){",
                "       return 0;",
                "   }",
                "   public int primitiveIntMethod(){",
                "       return 0;",
                "   }",
                "   public long primitiveLongMethod(){",
                "       return 0;",
                "   }",
                "   public float primitiveFloatMethod(){",
                "       return 0;",
                "   }",
                "   public double primitiveDoubleMethod(){",
                "       return 0;",
                "   }",
                "   public boolean primitiveBooleanMethod(){",
                "       return true;",
                "   }",
                "   public char primitiveCharMethod(){",
                "       return 0;",
                "   }",
                "   public Object simpleObjectMethod(){",
                "       return new Object();",
                "   }",
                "   public Long simplePrimitiveWrapperMethod(){",
                "       return new Long(1234);",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSimplePrimitiveClass", Joiner.on('\n').join(
                "package test;",
                "import java.lang.Long;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapClassStart("SimplePrimitiveClass"),
                objectOverriddenMethods("SimplePrimitiveClass"),

                "public byte primitiveByteMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveByteMethod()", "0"),

                "public short primitiveShortMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveShortMethod()", "0"),

                "public int primitiveIntMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveIntMethod()", "0"),

                "public long primitiveLongMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveLongMethod()", "0"),

                "public float primitiveFloatMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveFloatMethod()", "0"),

                "public double primitiveDoubleMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveDoubleMethod()", "0"),

                "public boolean primitiveBooleanMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveBooleanMethod()", "false"),

                "public char primitiveCharMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "primitiveCharMethod()", "0"),

                "public Object simpleObjectMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "simpleObjectMethod()", "null"),

                "public Long simplePrimitiveWrapperMethod(){",
                wrapperMethodBodyAndClose("SimplePrimitiveClass", "simplePrimitiveWrapperMethod()", "null"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testExtendedInterface() {
        JavaFileObject source1 = JavaFileObjects.forSourceString("com.test.SimpleInterface", Joiner.on('\n').join(
                "package com.test;",
                importWeakWrapAnnotation(),
                "public interface SimpleInterface {",
                "   void someTestMethod();",
                "}"
        ));

        JavaFileObject source2 = JavaFileObjects.forSourceString("test.SomeInterface", Joiner.on('\n').join(
                "package test;",
                "import com.test.SimpleInterface;",
                importWeakWrapAnnotation(),
                "public interface SomeInterface {",
                "    @WeakWrap",
                "    interface View extends SimpleInterface{",
                "    }",
                "}"
        ));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSomeInterfaceView", Joiner.on('\n').join(
                "package test;",
                importObjectMethodStuff(),
                importWeakReference(),

                wrapInterfaceStart("SomeInterface.View"),
                objectOverriddenMethods("SomeInterface.View"),

                "public void someTestMethod(){",
                wrapperMethodBodyAndClose("SomeInterface.View", "someTestMethod()"),

                clearWeakWrapRefMethod(),
                wrapperEnd()
        ));

        assertAbout(javaSources()).that(Arrays.asList(source1, source2))
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }


    private static String objectOverriddenMethods(String originalName) {
        String weakWrapGetToLocalVar = "    " + originalName + " original = weakWrap.get();";
        return Joiner.on('\n').join(
                "public int hashCode() {",
                weakWrapGetToLocalVar,
                "    if(original != null) {",
                "      return original.hashCode();",
                "    }",
                "    return 0;",
                "}",
                "public boolean equals(Object arg0) {",
                weakWrapGetToLocalVar,
                "    if(original != null) {",
                "      return original.equals(arg0);",
                "    }",
                "    return false;",
                "}",
                "public String toString() {",
                weakWrapGetToLocalVar,
                "    if(original != null) {",
                "      return original.toString();",
                "    }",
                "    return null;",
                "}");
    }

    private static String importObjectMethodStuff() {
        return Joiner.on('\n').join(
                "import java.lang.Object;",
                "import java.lang.String;");
    }

    private static String importWeakWrapAnnotation() {
        return "import com.stefandekanski.weakwrap.anotation.WeakWrap;";
    }

    private static String importWeakReference() {
        return "import java.lang.ref.WeakReference;";
    }

    private static String wrapInterfaceStart(String original) {
        return wrapperStart(original, true);
    }

    private static String wrapClassStart(String original) {
        return wrapperStart(original, false);
    }

    private static String wrapperStart(String original, boolean isInterface) {
        String extendOrImpl = isInterface ? "implements" : "extends";
        String classVarName = firstSmallLetterWithoutDots(original);
        String wrapClassName = "WeakWrap" + original.replaceAll("\\.", "");
        return Joiner.on('\n').join(
                "public class " + wrapClassName + " " + extendOrImpl + " " + original + "{",
                "private final WeakReference<" + original + "> weakWrap;",
                "public " + wrapClassName + "(" + original + " " + classVarName + ") {",
                "weakWrap = new WeakReference<>(" + classVarName + ");",
                "}");
    }

    private static String wrapperEnd() {
        return "}";
    }

    private static String firstSmallLetterWithoutDots(String string) {
        StringBuilder convertedString = new StringBuilder(string.length());
        convertedString.append(Character.toLowerCase(string.charAt(0)));
        for (int i = 1; i < string.length(); i++) {
            char ch = string.charAt(i);
            if (ch == '.') continue;
            convertedString.append(ch);
        }
        return convertedString.toString();
    }

    private static String wrapperMethodBodyAndClose(String originalClass, String methodCall) {
        return wrapperMethodBodyAndClose(originalClass, methodCall, "");
    }

    private static String wrapperMethodBodyAndClose(String originalClass, String methodCall, String returnVal) {
        String returnText = "";
        String returnStatement = "";
        if (returnVal.length() > 0) {
            returnText = "return ";
            returnStatement = "return " + returnVal + ";\n";
        }
        String method = Joiner.on('\n').join(
                "    " + originalClass + " original = weakWrap.get();",
                "    if(original != null){",
                "        " + returnText + "original." + methodCall + ";",
                "    }"
        );
        if (returnVal.length() > 0) {
            method += returnStatement;
        }
        return method + "\n}";
    }

    private static String clearWeakWrapRefMethod() {
        return Joiner.on('\n').join(
                "public void clearWeakWrapRef(){",
                "    weakWrap.clear();",
                "}"
        );
    }
}
