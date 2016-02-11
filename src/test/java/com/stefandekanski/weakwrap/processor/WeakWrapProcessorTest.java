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
        Set<String> supportedAnotationTypes = weakWrapProcessor.getSupportedAnnotationTypes();

        assertThat(supportedAnotationTypes.size(), is(1));
        assertThat(supportedAnotationTypes, hasItem(WeakWrap.class.getCanonicalName()));
    }

    @Test
    public void testEmptyClassWeakWrap_generatesClassWithPrefixWeakWrapConstructorAndClearWeakWrapMethod() {
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
}
