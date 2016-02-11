package com.stefandekanski.weakwrap.processor;

import com.google.common.base.Joiner;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.WeakReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class WeakWrapWriterTest {
    private static final String PACKAGE_NAME = "test";
    private static final String CLASS_NAME = "TestClassName";

    WeakWrapWriter weakWrapWriter;

    @Before
    public void createWeakWrapWriter() {
        weakWrapWriter = new WeakWrapWriter(PACKAGE_NAME, CLASS_NAME);
    }

    @Test
    public void testWeakWrapWriterConstructors() {
        WeakWrapWriter weakWrapWriter1 = new WeakWrapWriter(CLASS_NAME);
        assertThat(weakWrapWriter1.getPackageName(), is(""));
        assertThat(weakWrapWriter1.getWrapClassName(), is(WeakWrapWriter.PREFIX + CLASS_NAME));

        WeakWrapWriter weakWrapWriter2 = new WeakWrapWriter(PACKAGE_NAME, CLASS_NAME);
        assertThat(weakWrapWriter2.getPackageName(), is(PACKAGE_NAME));
        assertThat(weakWrapWriter2.getWrapClassName(), is(WeakWrapWriter.PREFIX + CLASS_NAME));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWeakWrapWriterConstructorNullParam1() {
        new WeakWrapWriter(null, CLASS_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWeakWrapWriterConstructorNullParam2() {
        new WeakWrapWriter(PACKAGE_NAME, null);
    }

    @Test
    public void testGeneratedConstructor() {
        MethodSpec constructor = weakWrapWriter.getConstructor();
        assertThat(constructor.toString(), is(expectedConstructor()));
    }

    @Test
    public void testGeneratedWeakWrapField() {
        FieldSpec weakWrapField = weakWrapWriter.getWeakWrapField();
        assertThat(weakWrapField.toString(), is(expectedWeakWrapField()));
    }

    private static String expectedConstructor() {
        String lowerCaseOriginalClassName = CLASS_NAME.toLowerCase();
        return Joiner.on('\n').join(
                "public Constructor(" + fullOriginalClassName() + " " + lowerCaseOriginalClassName + ") {",
                "  " + WeakWrapWriter.FIELD_NAME + " = new java.lang.ref.WeakReference<>(" + lowerCaseOriginalClassName + ");",
                "}",
                "");
    }

    private static String expectedWeakWrapField() {
        return "private final " + WeakReference.class.getCanonicalName() + "<" + PACKAGE_NAME + "." + CLASS_NAME + "> " + WeakWrapWriter.FIELD_NAME + ";\n";
    }

    private static String fullOriginalClassName() {
        return PACKAGE_NAME + "." + CLASS_NAME;
    }
}
