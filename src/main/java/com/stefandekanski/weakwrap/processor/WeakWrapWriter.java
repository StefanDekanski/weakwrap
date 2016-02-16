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
    public static class WeakWriterValidationException extends Exception {
        WeakWriterValidationException(String message) {
            super(message);
        }
    }

    public static final String CLASS_NAME_PREFIX = "WeakWrap";
    public static final String WEAK_REFERENCE_FIELD_NAME = "weakWrap";
    public static final String LOCAL_VAR_NAME = "original";

    private final String originalClassName;
    private final String wrapClassName;
    private final String packageName;

    private final TypeElement typeElement;

    public WeakWrapWriter(TypeElement typeElement) throws WeakWriterValidationException {
        checkIsValidElement(typeElement);
        this.typeElement = typeElement;
        this.packageName = extractPackageName(typeElement);
        this.originalClassName = extractClassName(packageName, typeElement);
        this.wrapClassName = CLASS_NAME_PREFIX + originalClassName.replaceAll("\\.", "");
    }

    public void writeWeakWrapperTo(Filer filer) throws IOException {
        MethodSpec constructor = createConstructor();
        FieldSpec weakWrapField = createWeakWrapField();
        List<MethodSpec> wrappedMethods = createWrappedMethods();

        TypeSpec wrappedType = TypeSpec.classBuilder(wrapClassName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructor)
                .addField(weakWrapField)
                .addMethods(wrappedMethods).build();

        JavaFile.builder(packageName, wrappedType).build().writeTo(filer);
    }

    private static void checkIsValidElement(TypeElement typeElement) throws WeakWriterValidationException {
        NestingKind nestingKind = typeElement.getNestingKind();
        boolean isTopLevel = nestingKind.equals(NestingKind.TOP_LEVEL);
        boolean isMemberKind = nestingKind.equals(NestingKind.MEMBER);
        boolean isStatic = typeElement.getModifiers().contains(Modifier.STATIC);

        //only allow topLevel and memberStatic
        if (!(isTopLevel || (isMemberKind && isStatic))) {
            throw new WeakWriterValidationException("Only Top level and static inner classes are supported");
        }
    }

    private static String extractPackageName(TypeElement typeElement) {
        TypeElement currentElement = typeElement;
        while (currentElement.getNestingKind().isNested()) {
            currentElement = (TypeElement) typeElement.getEnclosingElement();
        }
        PackageElement packageElem = (PackageElement) currentElement.getEnclosingElement();
        return packageElem.getQualifiedName().toString();
    }

    private static String extractClassName(String packageName, TypeElement typeElement) {
        String fullClassName = typeElement.getQualifiedName().toString();
        if (packageName.length() == 0) {
            return fullClassName;
        }
        return fullClassName.substring(packageName.length() + 1);
    }

    private List<MethodSpec> createWrappedMethods() {
        LinkedList<MethodSpec> wrappedMethods = new LinkedList<>();
        for (ExecutableElement method : getMethodList(typeElement)) {
            wrappedMethods.add(wrapMethod(method));
        }
        return wrappedMethods;
    }

    private MethodSpec createConstructor() {
        String varName = firstSmallLetterWithoutDots(originalClassName);
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(fullOriginalClassName(), varName)
                .addStatement(WEAK_REFERENCE_FIELD_NAME + " = new $T<>(" + varName + ")", WeakReference.class)
                .build();
    }

    private String firstSmallLetterWithoutDots(String string) {
        StringBuilder convertedString = new StringBuilder(string.length());
        convertedString.append(Character.toLowerCase(string.charAt(0)));
        for (int i = 1; i < string.length(); i++) {
            char ch = string.charAt(i);
            if (ch == '.') continue;
            convertedString.append(ch);
        }
        return convertedString.toString();
    }


    private FieldSpec createWeakWrapField() {
        ParameterizedTypeName fieldType = ParameterizedTypeName.get(ClassName.get(WeakReference.class), fullOriginalClassName());
        return FieldSpec.builder(fieldType, WeakWrapWriter.WEAK_REFERENCE_FIELD_NAME, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private ClassName fullOriginalClassName() {
        return ClassName.get(packageName, originalClassName);
    }

    private List<? extends ExecutableElement> getMethodList(TypeElement typeElement) {
        LinkedList<ExecutableElement> methods = new LinkedList<>();
        for (Element e : typeElement.getEnclosedElements()) {
            if (e.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) e;
                if (isValidWrapMethod(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private boolean isValidWrapMethod(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            return false;
        }
        return true;
    }

    private MethodSpec wrapMethod(ExecutableElement originalMethod) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(copyMethodName(originalMethod));
        copyMethodSignature(originalMethod, methodBuilder);
        addWrappedMethodBody(originalMethod, methodBuilder);
        return methodBuilder.build();
    }

    private void copyMethodSignature(ExecutableElement originalMethod, MethodSpec.Builder methodBuilder) {
        methodBuilder.addModifiers(copyMethodModifiers(originalMethod));
        methodBuilder.addTypeVariables(copyTypeParameters(originalMethod));
        methodBuilder.addParameters(copyMethodParameters(originalMethod));
        methodBuilder.addExceptions(copyMethodExceptions(originalMethod));
        methodBuilder.varargs(copyVarargs(originalMethod));
        methodBuilder.returns(copyReturnType(originalMethod));
    }

    private void addWrappedMethodBody(ExecutableElement originalMethod, MethodSpec.Builder methodBuilder) {
        methodBuilder.addStatement(getWeakReferenceToLocalVar());
        methodBuilder.beginControlFlow(ifLocalVarIsNotNull());
        methodBuilder.addStatement(addExecuteOriginalMethod(originalMethod));
        methodBuilder.endControlFlow();
        if (isReturnStatementNeeded(originalMethod)) {
            methodBuilder.addStatement(addReturnStatement(originalMethod));
        }
    }

    private Set<Modifier> copyMethodModifiers(ExecutableElement originalMethod) {
        Set<Modifier> modifiers = new LinkedHashSet<>(originalMethod.getModifiers());
        //exclude abstract and final modifiers
        modifiers.remove(Modifier.ABSTRACT);
        modifiers.remove(Modifier.FINAL);
        return modifiers;
    }

    private Iterable<TypeVariableName> copyTypeParameters(ExecutableElement originalMethod) {
        ArrayList<TypeVariableName> typeParameters = new ArrayList<>(originalMethod.getTypeParameters().size());
        for (TypeParameterElement typeParameterElement : originalMethod.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            typeParameters.add(TypeVariableName.get(var));
        }
        return typeParameters;
    }

    private Iterable<ParameterSpec> copyMethodParameters(ExecutableElement originalMethod) {
        ArrayList<ParameterSpec> methodParameters = new ArrayList<>(originalMethod.getParameters().size());
        for (VariableElement parameter : originalMethod.getParameters()) {
            TypeName type = TypeName.get(parameter.asType());
            String name = parameter.getSimpleName().toString();
            Set<Modifier> parameterModifiers = parameter.getModifiers();
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
                    .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
            methodParameters.add(parameterBuilder.build());
        }
        return methodParameters;
    }

    private Iterable<TypeName> copyMethodExceptions(ExecutableElement originalMethod) {
        ArrayList<TypeName> methodExceptions = new ArrayList<>(originalMethod.getThrownTypes().size());
        for (TypeMirror thrownType : originalMethod.getThrownTypes()) {
            methodExceptions.add(TypeName.get(thrownType));
        }
        return methodExceptions;
    }

    private boolean copyVarargs(ExecutableElement originalMethod) {
        return originalMethod.isVarArgs();
    }

    private TypeName copyReturnType(ExecutableElement originalMethod) {
        TypeMirror returnType = originalMethod.getReturnType();
        return TypeName.get(returnType);
    }

    private String getWeakReferenceToLocalVar() {
        return originalClassName + " " + LOCAL_VAR_NAME + " = " + WEAK_REFERENCE_FIELD_NAME + ".get()";
    }

    private String ifLocalVarIsNotNull() {
        return "if(" + LOCAL_VAR_NAME + " != null)";
    }

    private String addExecuteOriginalMethod(ExecutableElement originalMethod) {
        boolean returnNeeded = isReturnStatementNeeded(originalMethod);
        String originalMethodName = copyMethodName(originalMethod);
        Iterable<String> copiedParamNames = copyMethodParamNames(originalMethod);
        return executeOriginalMethod(returnNeeded, originalMethodName, copiedParamNames);
    }

    private String addReturnStatement(ExecutableElement originalMethod) {
        TypeKind returnKind = originalMethod.getReturnType().getKind();
        if (returnKind.isPrimitive()) {
            if (returnKind.equals(TypeKind.BOOLEAN)) {
                return "return false";
            } else {
                return "return 0";
            }
        } else {
            return "return null";
        }
    }

    private boolean isReturnStatementNeeded(ExecutableElement originalMethod) {
        TypeMirror returnType = originalMethod.getReturnType();
        return !(returnType.getKind().equals(TypeKind.VOID));
    }

    private String copyMethodName(ExecutableElement originalMethod) {
        return originalMethod.getSimpleName().toString();
    }

    private Iterable<String> copyMethodParamNames(ExecutableElement originalMethod) {
        ArrayList<String> methodParamNames = new ArrayList<>(originalMethod.getParameters().size());
        for (VariableElement parameter : originalMethod.getParameters()) {
            String name = parameter.getSimpleName().toString();
            methodParamNames.add(name);
        }
        return methodParamNames;
    }

    private String executeOriginalMethod(boolean isReturnNeeded, String originalMethodName, Iterable<String> originalParamNames) {
        String optReturn = isReturnNeeded ? "return" : "";
        String joinedOriginalParams = Joiner.on(',').join(originalParamNames);
        return optReturn + " " + LOCAL_VAR_NAME + "." + originalMethodName + "(" + joinedOriginalParams + ")";
    }

}
