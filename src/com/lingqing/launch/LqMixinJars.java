package com.lingqing.launch;

import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 扫描版本目录下的 {@code lqmixin/}，把里面的每个 jar 当成一个独立的 Mixin 配置。
 *
 * <p>jar 结构固定为：
 * <pre>
 * xxx.jar
 *  ├─ mixin.json      // Mixin 配置，package 指向 mixin 包
 *  └─ mixin/          // mixin 类
 *       SomeMixin.class
 * </pre>
 *
 * <p>所有 jar 里都叫 {@code mixin.json}，而 Mixin 是按“资源名”区分配置的，
 * 同名会互相覆盖，所以这里把每个 jar 的 mixin.json 复制到临时目录并重命名，
 * 再把临时目录和 jar 本体都挂到 LaunchClassLoader 上。
 */
final class LqMixinJars {

    private LqMixinJars() {
    }

    /**
     * @return 已注册的 Mixin 配置资源名列表（相对 classpath 根）
     */
    static List<String> install(LaunchClassLoader classLoader, File gameDir, String version) {
        List<String> configs = new ArrayList<String>();
        File dir = findMixinDir(gameDir, version);
        if (dir == null) {
            return configs;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return configs;
        }
        Arrays.sort(files);

        Path configDir;
        try {
            configDir = Files.createTempDirectory("lq-mixin-configs");
            configDir.toFile().deleteOnExit();
            classLoader.addURL(configDir.toUri().toURL());
        } catch (IOException e) {
            System.err.println("[lq-loader] 无法创建临时配置目录: " + e);
            return configs;
        }

        int index = 0;
        for (File jar : files) {
            if (!jar.isFile() || !jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }

            String baseName = jar.getName().substring(0, jar.getName().length() - 4);
            String configName = String.format("lq_%02d_%s.json", index, safeName(baseName));
            try {
                if (copyConfig(jar, configDir.resolve(configName))) {
                    classLoader.addURL(jar.toURI().toURL());
                    configs.add(configName);
                    index++;
                    System.out.println("[lq-loader] lqmixin: " + jar.getName() + " -> " + configName);
                }
            } catch (IOException e) {
                System.err.println("[lq-loader] 读取 " + jar.getName() + " 失败: " + e);
            }
        }
        return configs;
    }

    private static File findMixinDir(File gameDir, String version) {
        if (gameDir == null) {
            return null;
        }
        // 版本隔离时 --gameDir 就是版本目录；否则是 .minecraft
        File direct = new File(gameDir, "lqmixin");
        if (direct.isDirectory()) {
            return direct;
        }
        if (version != null && !version.isEmpty()) {
            File nested = new File(new File(new File(gameDir, "versions"), version), "lqmixin");
            if (nested.isDirectory()) {
                return nested;
            }
        }
        return null;
    }

    private static boolean copyConfig(File jar, Path target) throws IOException {
        JarFile jarFile = new JarFile(jar);
        try {
            JarEntry entry = jarFile.getJarEntry("mixin.json");
            if (entry == null) {
                System.err.println("[lq-loader] " + jar.getName() + " 里没有 mixin.json，已跳过");
                return false;
            }
            InputStream in = jarFile.getInputStream(entry);
            try {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                in.close();
            }
            target.toFile().deleteOnExit();
            return true;
        } finally {
            jarFile.close();
        }
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
