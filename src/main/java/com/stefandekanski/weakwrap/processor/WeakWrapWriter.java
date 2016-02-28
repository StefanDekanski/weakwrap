package com.stefandekanski.weakwrap.processor;

import com.google.common.base.Joiner;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

//TODO convert to builder, add configurable options
public class WeakWrapWriter {
    public static final String TYPE_VALIDATION_MSG = "Only Top level and static inner classes are supported!";

    public abstract static class WeakWrapValidationException extends Exception {
        public WeakWrapValidationException(String msg) {
            super(msg);
        }
    }

    public static class TypeValidationException extends WeakWrapValidationException {
        public TypeValidationException() {
            super(TYPE_VALIDATION_MSG);
        }
    }

    public static final String CLASS_NAME_PREFIX = "WeakWrap";
    public static final String WEAK_REFERENCE_FIELD_NAME = "weakWrap";
    public static final String LOCAL_VAR_NAME = "original";

    private final String originalClassName;
    private final String wrapClassName;
    private final String packageName;

    private final TypeElement typeElement;
    private final Elements elemUtil;

    public WeakWrapWriter(TypeElement typeElement, Elements elemUtil) throws TypeValidationException {
        checkIsValidType(typeElement);
        this.typeElement = typeElement;
        this.elemUtil = elemUtil;
        this.packageName = extractPackageName(elemUtil, typeElement);
        this.originalClassName = extractClassName(packageName, typeElement);
        this.wrapClassName = CLASS_NAME_PREFIX + originalClassName.replaceAll("\\.", "");
    }

    public void writeWeakWrapperTo(Filer filer) throws IOException {
        MethodSpec constructor = createConstructor();
        FieldSpec weakWrapField = createWeakWrapField();
        List<MethodSpec> wrappedMethods = createWrappedMethods();
        MethodSpec clearWeakWrapRefMethod = clearWeakWrapRefMethod();

        TypeSpec.Builder builder = TypeSpec.classBuilder(wrapClassName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructor)
                .addField(weakWrapField)
                .addMethods(wrappedMethods)
                .addMethod(clearWeakWrapRefMethod);

        if (isOriginalElementClass()) {
            builder.superclass(fullOriginalClassName());
        } else {
            builder.addSuperinterface(fullOriginalClassName());
        }
        JavaFile.builder(packageName, builder.build()).build().writeTo(filer);
    }

    private void checkIsValidType(TypeElement typeElement) throws TypeValidationException {
        NestingKind nestingKind = typeElement.getNestingKind();
        boolean isTopLevel = nestingKind.equals(NestingKind.TOP_LEVEL);
        boolean isMemberKind = nestingKind.equals(NestingKind.MEMBER);
        boolean isStatic = typeElement.getModifiers().contains(Modifier.STATIC);

        //only allow topLevel and memberStatic
        if (!(isTopLevel || (isMemberKind && isStatic))) {
            throw new TypeValidationException();
        }
    }

    private String extractPackageName(Elements elemUtil, TypeElement typeElement) {
        return elemUtil.getPackageOf(typeElement).getQualifiedName().toString();
    }

    private String extractClassName(String packageName, TypeElement typeElement) {
        String fullClassName = typeElement.getQualifiedName().toString();
        if (packageName.length() == 0) {
            return fullClassName;
        }
        return fullClassName.substring(packageName.length() + 1);
    }

    private boolean isOriginalElementClass() {
        return typeElement.getKind().isClass();
    }

    private MethodSpec createConstructor() {
        String varName = firstSmallLetterWithoutDots(originalClassName);
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(fullOriginalClassName(), varName)
                .addStatement(declareWeakReference(varName), WeakReference.class)
                .build();
    }

    private String declareWeakReference(String varName) {
        return WEAK_REFERENCE_FIELD_NAME + " = new $T<>(" + varName + ")";
    }

    private FieldSpec createWeakWrapField() {
        ParameterizedTypeName fieldType = ParameterizedTypeName.get(ClassName.get(WeakReference.class), fullOriginalClassName());
        return FieldSpec.builder(fieldType, WeakWrapWriter.WEAK_REFERENCE_FIELD_NAME, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private ClassName fullOriginalClassName() {
        return ClassName.get(packageName, originalClassName);
    }

    private List<MethodSpec> createWrappedMethods() {
        LinkedList<MethodSpec> wrappedMethods = new LinkedList<>();
        for (ExecutableElement method : getMethodList()) {
            wrappedMethods.add(wrapMethod(method));
        }
        return wrappedMethods;
    }

    public MethodSpec clearWeakWrapRefMethod() {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("clearWeakWrapRef");
        methodBuilder.addModifiers(Modifier.PUBLIC);
        methodBuilder.addStatement(WEAK_REFERENCE_FIELD_NAME + ".clear()");
        return methodBuilder.build();
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

    private List<? extends ExecutableElement> getMethodList() {
        LinkedList<ExecutableElement> methods = new LinkedList<>();
        for (Element e : getAllMethodsSet()) {
            ExecutableElement method = (ExecutableElement) e;
            if (canOverride(method)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private Set<? extends Element> getAllMethodsSet() {
        return ElementFilter.methodsIn(new LinkedHashSet<>(elemUtil.getAllMembers(typeElement)));
    }

    private boolean canOverride(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.PROTECTED)) {
            String methodPackage = elemUtil.getPackageOf(method).getQualifiedName().toString();
            if (!methodPackage.equals(packageName)) {
                return false;
            }
        }
        return !(modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.FINAL));
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
        modifiers.remove(Modifier.ABSTRACT);
        modifiers.remove(Modifier.NATIVE);
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
            }
            return "return 0";
        }
        return "return null";
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
