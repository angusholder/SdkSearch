/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.reflect.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import dagger.reflect.DaggerReflect;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static java.util.Collections.singleton;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
public final class DaggerReflectCompiler extends AbstractProcessor {
  private static final ClassName DAGGER_REFLECT = ClassName.get(DaggerReflect.class);

  private Filer filer;
  private Messager messager;

  @Override public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
    messager = processingEnv.getMessager();
  }

  @Override public Set<String> getSupportedAnnotationTypes() {
    return singleton(Component.class.getCanonicalName());
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> candidates = roundEnv.getElementsAnnotatedWith(Component.class);
    for (Element candidate : candidates) {
      TypeElement component = (TypeElement) candidate;
      ClassName componentName = ClassName.get(component);
      TypeElement builder = findBuilder(component);
      ClassName builderName = builder != null ? ClassName.get(builder) : null;

      TypeSpec type = createComponent(componentName, builderName)
          .toBuilder()
          .addOriginatingElement(component)
          .build();
      JavaFile file = JavaFile.builder(componentName.packageName(), type)
          .addFileComment("Generated by Dagger's reflect-compiler. Do not modify!")
          .build();
      try {
        file.writeTo(filer);
      } catch (Exception e) {
        messager.printMessage(ERROR, "Unable to write component implementation: " + e, component);
      }
    }
    return false;
  }

  private static TypeElement findBuilder(TypeElement component) {
    for (Element enclosed : component.getEnclosedElements()) {
      if (enclosed.getAnnotation(Component.Builder.class) != null) {
        return (TypeElement) enclosed;
      }
    }
    return null;
  }

  private static TypeSpec createComponent(ClassName component, ClassName builder) {
    TypeSpec.Builder type = TypeSpec.classBuilder("Dagger" + component.simpleName())
        .addModifiers(PUBLIC, FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(PRIVATE)
            .addStatement("throw new $T()", AssertionError.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(component)
            .addStatement("return $T.create($T.class)", DAGGER_REFLECT, component)
            .build());
    if (builder != null) {
      type.addMethod(MethodSpec.methodBuilder("builder")
          .addModifiers(PUBLIC, STATIC)
          .returns(builder)
          .addStatement("return $T.builder($T.class)", DAGGER_REFLECT, builder)
          .build());
    }
    return type.build();
  }
}