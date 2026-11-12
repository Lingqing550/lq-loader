package com.lingqing;

import net.minecraft.launchwrapper.Launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public final class Main {

    private Main() {
    }

    public static void main(String[] args) {

        Launch.main(withTweaks(args));
    }

    /**
     * 在启动器传入的游戏参数前插入 tweakClass。
     *
     * <p>LqTweaker 必须排第一：LaunchWrapper 用第一个 tweak 的
     * {@code getLaunchTarget()} 决定启动类；MixinTweaker 排第二，负责初始化
     * Mixin 子系统并把 Proxy 转换器注册到 LaunchClassLoader。
     */
    static String[] withTweaks(String[] gameArgs) {
        List<String> args = new ArrayList<String>();
        args.add("--tweakClass");
        args.add("com.lingqing.launch.LqTweaker");
        args.add("--tweakClass");
        args.add("org.spongepowered.asm.launch.MixinTweaker");
        args.addAll(Arrays.asList(gameArgs));
        return args.toArray(new String[0]);
    }
}
