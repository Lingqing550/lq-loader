package com.lingqing.launch;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LaunchWrapper tweaker：接管启动参数并把 com.lingqing.mixin 配置挂进 Mixin 子系统。
 *
 * <p>必须作为第一个 --tweakClass 传入，因为它决定了 {@link #getLaunchTarget()}
 * 返回的启动类；{@code org.spongepowered.asm.launch.MixinTweaker} 排在它后面，
 * 负责初始化 Mixin 并把 Proxy 转换器注册到 LaunchClassLoader。
 */
public final class LqTweaker implements ITweaker {

    private final List<String> gameArgs = new ArrayList<String>();
    private File gameDir;
    private File assetsDir;
    private String version;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // Launch 会把 --version/--gameDir/--assetsDir 这些它自己认识的参数吃掉，
        // 剩下的（--username/--uuid/--assetIndex...）从这里透传给游戏。
        this.gameArgs.addAll(args);
        this.gameDir = gameDir;
        this.assetsDir = assetsDir;
        this.version = profile;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        // JNA 从系统 classpath 加载，避免和游戏内的 JNA 出现两份
        classLoader.addClassLoaderExclusion("com.sun.jna.");
        // 资源名相对 classpath 根；文件是 src/mixins.json。
        Mixins.addConfiguration("mixins.json");

        // 版本目录下 lqmixin/*.jar：每个 jar 一个 mixin.json + mixin/ 文件夹
        for (String config : LqMixinJars.install(classLoader, this.gameDir, this.version)) {
            Mixins.addConfiguration(config);
        }
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        List<String> args = new ArrayList<String>(this.gameArgs);
        if (this.version != null) {
            args.add("--version");
            args.add(this.version);
        }
        if (this.gameDir != null) {
            args.add("--gameDir");
            args.add(this.gameDir.getAbsolutePath());
        }
        if (this.assetsDir != null) {
            args.add("--assetsDir");
            args.add(this.assetsDir.getAbsolutePath());
        }
        return args.toArray(new String[0]);
    }
}
