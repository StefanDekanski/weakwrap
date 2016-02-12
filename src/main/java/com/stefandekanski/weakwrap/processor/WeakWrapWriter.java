package com.stefandekanski.weakwrap.processor;

import com.google.common.base.Joiner;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

//TODO convert to builder, add configurable options
public class WeakWrapWriter {
    public static final String PREFIX = "WeakWrap";
    public static final String FIELD_NAME = "weakWrap";

    private final String originalClassName;
    private final String wrapClassName;
    private final String packageName;

    private final TypeElement typeElement;

    public WeakWrapWriter(TypeElement typeElement) {
        this.typeElement = typeElement;
        this.originalClassName = typeElement.getSimpleName().toString();
        this.wrapClassName = PREFIX + originalClassName;
        this.packageName = extractPackageName(typeElement.getQualifiedName(), typeElement.getSimpleName());
    }

    public void writeWeakWrapperTo(Filer filer) throws IOException {
        MethodSpec constructor = createConstructor(originalClassName);
        FieldSpec weakWrapField = createWeakWrapField(originalClassName);
        List<MethodSpec> wrappedMethods = createWrappedMethods(typeElement);

        TypeSpec wrappedType = TypeSpec.classBuilder(wrapClassName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructor)
                .addField(weakWrapField)
                .addMethods(wrappedMethods).build();

        JavaFile.builder(packageName, wrappedType).build().writeTo(filer);
    }

    private static String extractPackageName(Name fullName, Name className) {
        String fullNameString = fullName.toString();
        return fullNameString.substring(0, fullName.length() - className.length() - 1);
    }

    private List<MethodSpec> createWrappedMethods(TypeElement typeElement) {
        LinkedList<MethodSpec> wrappedMethods = new LinkedList<>();
        for (ExecutableElement method : getMethodList(typeElement)) {
            wrappedMethods.add(wrapMethod(method));
        }
        return wrappedMethods;
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

    private List<? extends ExecutableElement> getMethodList(TypeElement typeElement) {
        LinkedList<ExecutableElement> methods = new LinkedList<>();
        for (Element e : typeElement.getEnclosedElements()) {
            if (e.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) e;
                if (validWrapMethod(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private boolean validWrapMethod(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            return false;
        }
        return true;
    }

    private MethodSpec wrapMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

        Set<Modifier> modifiers = method.getModifiers();
        modifiers = new LinkedHashSet<>(modifiers);
        modifiers.remove(Modifier.ABSTRACT);
        modifiers.remove(Modifier.FINAL);
        methodBuilder.addModifiers(modifiers);

        for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

        TypeMirror returnType = method.getReturnType();
        methodBuilder.returns(TypeName.get(returnType));

        List<? extends VariableElement> parameters = method.getParameters();
        List<String> paramNames = new ArrayList<>(parameters.size());
        for (VariableElement parameter : parameters) {
            TypeName type = TypeName.get(parameter.asType());
            String name = parameter.getSimpleName().toString();
            paramNames.add(name);
            Set<Modifier> parameterModifiers = parameter.getModifiers();
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
                    .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
            methodBuilder.addParameter(parameterBuilder.build());
        }
        methodBuilder.varargs(method.isVarArgs());

        for (TypeMirror thrownType : method.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
        }

        boolean returnNeeded = true;
        if (returnType.getKind().equals(TypeKind.VOID)) {
            returnNeeded = false;
        }

        methodBuilder
                .addStatement(originalClassName + " original = " + FIELD_NAME + ".get()")
                .beginControlFlow("if(original != null)");
        String optReturn = returnNeeded ? "return" : "";
        methodBuilder.addStatement(optReturn + " original." + methodName + "(" + Joiner.on(',').join(paramNames) + ")");
        methodBuilder.endControlFlow();

        if (returnNeeded) {
            if (returnType.getKind().isPrimitive()) {
                if (returnType.getKind().equals(TypeKind.BOOLEAN)) {
                    methodBuilder.addStatement("return false");
                } else {
                    methodBuilder.addStatement("return 0");
                }
            } else {
                methodBuilder.addStatement("return null");
            }
        }
        return methodBuilder.build();
    }
}
