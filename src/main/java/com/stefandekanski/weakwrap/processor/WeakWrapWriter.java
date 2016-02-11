package com.stefandekanski.weakwrap.processor;

import com.google.common.base.Preconditions;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WeakWrapWriter {
    public static final String PREFIX = "WeakWrap";
    public static final String FIELD_NAME = "weakWrap";

    private final String wrapClassName;
    private final String packageName;

    private final MethodSpec constructor;
    private final FieldSpec weakWrapField;

    public WeakWrapWriter(String originalClassName) {
        this("", originalClassName);
    }

    public WeakWrapWriter(String packageName, String originalClassName) {
        Preconditions.checkArgument(packageName != null && originalClassName != null);
        this.wrapClassName = PREFIX + originalClassName;
        this.packageName = packageName;
        this.constructor = createConstructor(originalClassName);
        this.weakWrapField = createWeakWrapField(originalClassName);
    }

    public String getWrapClassName() {
        return wrapClassName;
    }

    public String getPackageName() {
        return packageName;
    }

    public MethodSpec getConstructor() {
        return constructor;
    }

    public FieldSpec getWeakWrapField() {
        return weakWrapField;
    }

    public void writeWeakWrapperTo(Filer filer) throws IOException {
        TypeSpec weakWrapped = TypeSpec.classBuilder(wrapClassName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructor)
                .addField(weakWrapField)
                .build();
        JavaFile.builder(packageName, weakWrapped).build().writeTo(filer);
    }


    public void addMethod(ExecutableElement executableElement) {
        Set<Modifier> modifiers = executableElement.getModifiers();
        TypeMirror returnType = executableElement.getReturnType();
        List<ParameterSpec> parameterSpecs = getParametersSpecs(executableElement.getParameters());
        String methodName = executableElement.getSimpleName().toString();

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(modifiers)
                .returns(TypeName.get(returnType))
                .addParameters(parameterSpecs);

        for (ParameterSpec p : parameterSpecs) {

        }
    }

    private MethodSpec createConstructor(String originalClassName) {
        String lowerCaseOriginalClassName = originalClassName.toLowerCase();
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(
                        ClassName.get(packageName, originalClassName),
                        lowerCaseOriginalClassName)
                .addStatement(FIELD_NAME + " = new $T<>(" + lowerCaseOriginalClassName + ")", WeakReference.class)
                .build();
    }

    private FieldSpec createWeakWrapField(String originalClassName) {
        ParameterizedTypeName fieldType = ParameterizedTypeName.get(ClassName.get(WeakReference.class), ClassName.get(packageName, originalClassName));
        return FieldSpec.builder(fieldType, WeakWrapWriter.FIELD_NAME, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private List<ParameterSpec> getParametersSpecs(List<? extends VariableElement> methodParams) {
        List<ParameterSpec> parameterSpecs = new ArrayList<>(methodParams.size());
        for (VariableElement v : methodParams) {
            parameterSpecs.add(
                    ParameterSpec.builder(
                            TypeName.get(v.asType()),
                            v.getSimpleName().toString()
                    ).build());
        }
        return parameterSpecs;
    }

}
