package io.triform.indexer;

import io.triform.rpc.annotation.RemoteFacade;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编译期生成 Facade 索引（E3）：扫描本轮编译中标了 {@link RemoteFacade} 的接口，
 * 把 FQN 列表写入 CLASS_OUTPUT 的 {@code META-INF/triform-facades}——消费方启动按索引毫秒级发现契约。
 *
 * <p>用法：api 契约模块加 provided 依赖本模块即可（javac 自动发现处理器），无需手工维护索引文件。
 * 增量编译时与输出目录既有索引合并（读取现存条目去重），避免部分重编产出子集索引。
 */
@SupportedAnnotationTypes("io.triform.rpc.annotation.RemoteFacade")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class TriformFacadeIndexProcessor extends AbstractProcessor {

    private static final String INDEX_PATH = "META-INF/triform-facades";

    private final Set<String> facades = new LinkedHashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(RemoteFacade.class)) {
            if (element.getKind() == ElementKind.INTERFACE) {
                facades.add(((TypeElement) element).getQualifiedName().toString());
            } else {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING,
                        "@RemoteFacade 应标注在接口上, 索引忽略: " + element, element);
            }
        }
        if (roundEnv.processingOver() && !facades.isEmpty()) {
            writeIndex();
        }
        return false;
    }

    private void writeIndex() {
        try {
            Set<String> merged = new LinkedHashSet<>(readExisting());
            merged.addAll(facades);
            FileObject resource;
            try {
                resource = processingEnv.getFiler().createResource(
                        StandardLocation.CLASS_OUTPUT, "", INDEX_PATH);
            } catch (FilerException alreadyExists) {
                // 输出目录已有本文件(上次构建/资源拷贝) —— 直接覆写
                resource = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_OUTPUT, "", INDEX_PATH);
            }
            try (Writer writer = resource.openWriter()) {
                for (String fqn : merged) {
                    writer.write(fqn);
                    writer.write('\n');
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR,
                    "写入 Facade 索引失败: " + e.getMessage());
        }
    }

    private Set<String> readExisting() {
        Set<String> existing = new LinkedHashSet<>();
        try {
            FileObject resource = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", INDEX_PATH);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.openInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        existing.add(line);
                    }
                }
            }
        } catch (IOException noExisting) {
            // 首次生成, 无现存索引
        }
        return existing;
    }
}
