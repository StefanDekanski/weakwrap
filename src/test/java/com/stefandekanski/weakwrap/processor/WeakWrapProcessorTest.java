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
    public void testEmptyClass() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.EmptyClass", Joiner.on('\n').join(
                "package test;",
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "public class EmptyClass {",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapEmptyClass", Joiner.on('\n').join(
                "package test;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapEmptyClass {",
                "    private final WeakReference<EmptyClass> weakWrap;",
                "    public WeakWrapEmptyClass(EmptyClass emptyclass) {",
                "        weakWrap = new WeakReference<>(emptyclass);",
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
                "public class WeakWrapSimpleInterface {",
                "    private final WeakReference<SimpleInterface> weakWrap;",
                "    public WeakWrapSimpleInterface(SimpleInterface simpleinterface) {",
                "        weakWrap = new WeakReference<>(simpleinterface);",
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
    public void testClassModifiers() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.ModifiersClass", Joiner.on('\n').join(
                "package test;",
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "@WeakWrap",
                "public abstract class ModifiersClass {",
                "   private void somePrivateMethod() {",
                "   }",
                "   public static void someStaticMethod(){",
                "   }",
                "   void defaultMethod(){",
                "   }",
                "   public abstract void someAbstractMethod();",
                "   public final void someFinalMethod(){",
                "   }",
                "   protected void someProtectedMethod(){",
                "   }",
                "   protected abstract void protectedAbstractMethod();",
                "}"));

        JavaFileObject expectedSource = JavaFileObjects.forSourceString("test.WeakWrapModifiersClass", Joiner.on('\n').join(
                "package test;",
                "import java.lang.ref.WeakReference;",
                "public class WeakWrapModifiersClass {",
                "    private final WeakReference<ModifiersClass> weakWrap;",
                "    public WeakWrapPrivateStatic(ModifiersClass modifiersclass) {",
                "        weakWrap = new WeakReference<>(modifiersclass);",
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
                "   public void someFinalMethod(){",
                "       ModifiersClass original = weakWrap.get();",
                "       if(original != null){",
                "           original.someFinalMethod();",
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
    public void testClassWithVoidMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleClass", Joiner.on('\n').join(
                "package test;",
                "import com.stefandekanski.weakwrap.anotation.WeakWrap;",
                "import java.util.Collection;",
                "import java.util.List;",
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
                "public class WeakWrapSimpleClass {",
                "    private final WeakReference<SimpleClass> weakWrap;",
                "    public WeakWrapEmptyClass(SimpleClass simpleclass) {",
                "        weakWrap = new WeakReference<>(simpleclass);",
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
                "public class WeakWrapSimpleGenericMethods {",
                "    private final WeakReference<SimpleGenericMethods> weakWrap;",
                "    public WeakWrapEmptyClass(SimpleGenericMethods simplegenericmethods) {",
                "        weakWrap = new WeakReference<>(simplegenericmethods);",
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
                "public class WeakWrapSimplePrimitiveClass {",
                "   private final WeakReference<SimplePrimitiveClass> weakWrap;",
                "   public WeakWrapEmptyClass(SimplePrimitiveClass simpleprimitiveclass) {",
                "        weakWrap = new WeakReference<>(simpleprimitiveclass);",
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
