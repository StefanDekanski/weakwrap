package com.stefandekanski.weakwrap.processor;

import com.google.auto.service.AutoService;
import com.stefandekanski.weakwrap.anotation.WeakWrap;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
public class WeakWrapProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(WeakWrap.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(WeakWrap.class);
        try {
            for (Element e : elements) {
                TypeElement typeElement = (TypeElement) e;
                WeakWrapWriter weakWrapWriter = new WeakWrapWriter(typeElement);
                weakWrapWriter.writeWeakWrapperTo(filer);
            }
        } catch (IOException | WeakWrapWriter.WeakWriterValidationException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
        return true;
    }
}
