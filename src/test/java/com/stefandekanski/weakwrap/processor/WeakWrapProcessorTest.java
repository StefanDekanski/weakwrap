package com.stefandekanski.weakwrap.processor;


import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import com.stefandekanski.weakwrap.anotation.WeakWrap;
import org.junit.Before;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.Set;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "public interface SomeInterface {",
                "    @WeakWrap",
                "    interface View {",
                "    }",
                "}"
        ));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("com.very.deep.something.WeakWrapSomeInterfaceView", Joiner.on('\n').join(
                "package com.very.deep.something;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSomeInterfaceView implements SomeInterface.View{",
                "    private final WeakReference<SomeInterface.View> weakWrap;",
                "    public WeakWrapSomeInterfaceView(SomeInterface.View someInterfaceView) {",
                "        weakWrap = new WeakReference<>(someInterfaceView);",
                "    }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "public class EmptyClass {",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("WeakWrapEmptyClass", Joiner.on('\n').join(
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapEmptyClass extends EmptyClass{",
                "    private final WeakReference<EmptyClass> weakWrap;",
                "    public WeakWrapEmptyClass(EmptyClass emptyClass) {",
                "        weakWrap = new WeakReference<>(emptyClass);",
                "    }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "interface SimpleInterface {",
                "   void someMethod();",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleInterface", Joiner.on('\n').join(
                "package test;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSimpleInterface implements SimpleInterface{",
                "    private final WeakReference<SimpleInterface> weakWrap;",
                "    public WeakWrapSimpleInterface(SimpleInterface simpleInterface) {",
                "        weakWrap = new WeakReference<>(simpleInterface);",
                "    }",
                "   public void someMethod(){",
                "       SimpleInterface original = weakWrap.get();",
                "       if(original != null){",
                "           original.someMethod();",
                "       }",
                "   }",
                "}"));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }

    @Test
    public void testFinalMethodModifier() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.FinalMethod", Joiner.on('\n').join(
                "package test;",
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "public class FinalMethod {",
                "   public final void someFinalMethod(){",
                "   }",
                "}"));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .failsToCompile()
                .withErrorContaining(WeakWrapWriter.METHOD_VALIDATION_MSG);
    }

    @Test
    public void testClassModifiers() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.ModifiersClass", Joiner.on('\n').join(
                "package test;",
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapModifiersClass extends ModifiersClass{",
                "    private final WeakReference<ModifiersClass> weakWrap;",
                "    public WeakWrapPrivateStatic(ModifiersClass modifiersClass) {",
                "        weakWrap = new WeakReference<>(modifiersClass);",
                "    }",
                "   void defaultMethod(){",
                "       ModifiersClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.defaultMethod();",
                "       }",
                "   }",
                "   public void someAbstractMethod(){",
                "       ModifiersClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.someAbstractMethod();",
                "       }",
                "   }",
                "   protected void someProtectedMethod(){",
                "       ModifiersClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.someProtectedMethod();",
                "       }",
                "   }",
                "   protected void protectedAbstractMethod(){",
                "       ModifiersClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.protectedAbstractMethod();",
                "       }",
                "   }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapClassWithMethodsThatThrowExceptions extends ClassWithMethodsThatThrowExceptions{",
                "    private final WeakReference<ClassWithMethodsThatThrowExceptions> weakWrap;",
                "    public WeakWrapEmptyClass(ClassWithMethodsThatThrowExceptions classWithMethodsThatThrowExceptions) {",
                "        weakWrap = new WeakReference<>(classWithMethodsThatThrowExceptions);",
                "    }",
                "   public void throwMethod() throws IOException,InterruptedException{",
                "       ClassWithMethodsThatThrowExceptions original = weakWrap.get();",
                "       if(original != null){",
                "           original.throwMethod();",
                "       }",
                "   }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSimpleClass extends SimpleClass{",
                "    private final WeakReference<SimpleClass> weakWrap;",
                "    public WeakWrapEmptyClass(SimpleClass simpleClass) {",
                "        weakWrap = new WeakReference<>(simpleClass);",
                "    }",
                "   public void simpleMethod(){",
                "       SimpleClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleMethod();",
                "       }",
                "   }",
                "   public void simpleParam(int par){",
                "       SimpleClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleParam(par);",
                "       }",
                "   }",
                "   public void simpleMultipleParam(int in,long lo,double dou){",
                "       SimpleClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleMultipleParam(in,lo,dou);",
                "       }",
                "   }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "public class ClassWithVarargs {",
                "   public void varargMethod(int a,Object... objectVararg){",
                "   }",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapClassClassWithVarargs", Joiner.on('\n').join(
                "package test;",
                "import java.lang.Object;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapClassWithVarargs extends ClassWithVarargs{",
                "    private final WeakReference<ClassWithVarargs> weakWrap;",
                "    public WeakWrapEmptyClass(ClassWithVarargs classWithVarargs) {",
                "        weakWrap = new WeakReference<>(classWithVarargs);",
                "    }",
                "   public void varargMethod(int a,Object... objectVararg){",
                "       ClassWithVarargs original = weakWrap.get();",
                "       if(original != null){",
                "           original.varargMethod(a,objectVararg);",
                "       }",
                "   }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSimpleClassInnerClass extends SimpleClass.InnerClass{",
                "    private final WeakReference<SimpleClass.InnerClass> weakWrap;",
                "    public WeakWrapSimpleClassInnerClass(SimpleClass.InnerClass simpleClassInnerClass) {",
                "        weakWrap = new WeakReference<>(simpleClassInnerClass);",
                "    }",
                "}"));

        JavaFileObject expectedInterfaceSource = JavaFileObjects.forSourceString("test.WeakWrapSimpleClassInnerInterface", Joiner.on('\n').join(
                "package test;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSimpleClassInnerInterface implements SimpleClass.InnerInterface{",
                "    private final WeakReference<SimpleClass.InnerInterface> weakWrap;",
                "    public WeakWrapSimpleClassInnerInterface(SimpleClass.InnerInterface simpleClassInnerInterface) {",
                "        weakWrap = new WeakReference<>(simpleClassInnerInterface);",
                "    }",
                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.ref.WeakReference;",
                "import java.util.Collection;",
                "import java.util.List;",
                "public class WeakWrapSimpleGenericMethods extends SimpleGenericMethods{",
                "    private final WeakReference<SimpleGenericMethods> weakWrap;",
                "    public WeakWrapEmptyClass(SimpleGenericMethods simpleGenericMethods) {",
                "        weakWrap = new WeakReference<>(simpleGenericMethods);",
                "    }",
                "   public void simpleGenericParam(List<Long> longList){",
                "       SimpleGenericMethods original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleGenericParam(longList);",
                "       }",
                "   }",
                "   public void simpleWildCard(Collection<? extends Number> col){",
                "       SimpleGenericMethods original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleWildCard(col);",
                "       }",
                "   }",
                "   public <K, V> void simpleGenericMethod(K key, V val) {",
                "       SimpleGenericMethods original = weakWrap.get();",
                "       if(original != null){",
                "           original.simpleGenericMethod(key,val);",
                "       }",
                "    }",
                "   public <T extends Comparable<T>> void genericMethod(T[] anArray, T elem){",
                "       SimpleGenericMethods original = weakWrap.get();",
                "       if(original != null){",
                "           original.genericMethod(anArray,elem);",
                "       }",
                "    }",

                "}"));

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
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
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
                "import java.lang.Object;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapSimplePrimitiveClass extends SimplePrimitiveClass{",
                "   private final WeakReference<SimplePrimitiveClass> weakWrap;",
                "   public WeakWrapEmptyClass(SimplePrimitiveClass simplePrimitiveClass) {",
                "        weakWrap = new WeakReference<>(simplePrimitiveClass);",
                "    }",
                "   public byte primitiveByteMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveByteMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public short primitiveShortMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveShortMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public int primitiveIntMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveIntMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public long primitiveLongMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveLongMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public float primitiveFloatMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveFloatMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public double primitiveDoubleMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveDoubleMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public boolean primitiveBooleanMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveBooleanMethod();",
                "       }",
                "       return false;",
                "   }",
                "   public char primitiveCharMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.primitiveCharMethod();",
                "       }",
                "       return 0;",
                "   }",
                "   public Object simpleObjectMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.simpleObjectMethod();",
                "       }",
                "       return null;",
                "   }",
                "   public Long simplePrimitiveWrapperMethod(){",
                "       SimplePrimitiveClass original = weakWrap.get();",
                "       if(original != null){",
                "          return original.simplePrimitiveWrapperMethod();",
                "       }",
                "       return null;",
                "   }",
                "}"));

        assertAbout(javaSource()).that(source)
                .processedWith(weakWrapProcessor)
                .compilesWithoutError()
                .and()
                .generatesSources(expectedSource);
    }
}
