package io.triform.core.boot;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean 来源审计:同 FQN 类在 classpath 出现多个来源时须报冲突,单一/缺失来源不误报。
 */
class BeanSourceAuditorTest {

    private static final String FQN = "io.triform.audit.Sample";

    @TempDir
    Path rootA;

    @TempDir
    Path rootB;

    private void writeSampleClass(Path root) throws Exception {
        Path pkg = root.resolve("io/triform/audit");
        Files.createDirectories(pkg);
        Files.write(pkg.resolve("Sample.class"), new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    }

    private URLClassLoader loaderFor(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) {
            urls[i] = roots[i].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }

    @Test
    void duplicateClassAcrossTwoRootsIsReported() throws Exception {
        writeSampleClass(rootA);
        writeSampleClass(rootB);
        try (URLClassLoader cl = loaderFor(rootA, rootB)) {
            Map<String, List<String>> conflicts = BeanSourceAuditor.findConflicts(cl, Collections.singletonList(FQN));
            assertEquals(1, conflicts.size());
            assertEquals(2, conflicts.get(FQN).size());
        }
    }

    @Test
    void singleSourceIsClean() throws Exception {
        writeSampleClass(rootA);
        try (URLClassLoader cl = loaderFor(rootA, rootB)) {
            Map<String, List<String>> conflicts = BeanSourceAuditor.findConflicts(cl, Collections.singletonList(FQN));
            assertTrue(conflicts.isEmpty());
        }
    }

    @Test
    void missingClassDoesNotFail() throws Exception {
        try (URLClassLoader cl = loaderFor(rootA)) {
            Map<String, List<String>> conflicts = BeanSourceAuditor.findConflicts(cl, Collections.singletonList("io.triform.audit.NotExist"));
            assertTrue(conflicts.isEmpty());
        }
    }

    @Test
    void collectBusinessBeanClassNamesFiltersByPrefix() {
        org.springframework.beans.factory.config.BeanDefinition biz =
                org.mockito.Mockito.mock(org.springframework.beans.factory.config.BeanDefinition.class);
        org.mockito.Mockito.when(biz.getBeanClassName()).thenReturn("io.triform.core.Foo");
        org.springframework.beans.factory.config.BeanDefinition thirdParty =
                org.mockito.Mockito.mock(org.springframework.beans.factory.config.BeanDefinition.class);
        org.mockito.Mockito.when(thirdParty.getBeanClassName()).thenReturn("org.springframework.Bar");
        org.springframework.beans.factory.config.BeanDefinition anonymous =
                org.mockito.Mockito.mock(org.springframework.beans.factory.config.BeanDefinition.class);
        org.mockito.Mockito.when(anonymous.getBeanClassName()).thenReturn(null);

        org.springframework.beans.factory.support.BeanDefinitionRegistry registry =
                org.mockito.Mockito.mock(org.springframework.beans.factory.support.BeanDefinitionRegistry.class);
        org.mockito.Mockito.when(registry.getBeanDefinitionNames()).thenReturn(new String[]{"biz", "thirdParty", "anonymous"});
        org.mockito.Mockito.when(registry.getBeanDefinition(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    if ("biz".equals(name)) {
                        return biz;
                    }
                    return "thirdParty".equals(name) ? thirdParty : anonymous;
                });

        List<String> names = BeanSourceAuditor.collectBusinessBeanClassNames(registry);
        assertEquals(Collections.singletonList("io.triform.core.Foo"), names);
    }
}
