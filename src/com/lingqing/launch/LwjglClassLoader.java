package com.lingqing.launch;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.LogWrapper;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * 让 {@code org.lwjgl.*} 也归 {@link LaunchClassLoader} 管，保证整个游戏只有一份类图。
 *
 * <p>LaunchWrapper 在构造 {@code LaunchClassLoader} 时写死了「交给父加载器」的包前缀，
 * 其中包含 {@code org.lwjgl.}。这本来是给普通 1.8.9 用的，但这个客户端把 LWJGL2 兼容层
 * （{@code org.lwjgl.opengl.Display}、{@code org.lwjgl.opengl.WindowEventQueue}、
 * {@code org.lwjgl.input.*}、{@code org.lwjgl.opengl.Win32BufferedRawInput} 等）
 * 连同 Win32 辅助类一起放进了自己的 jar，而这些类会反向引用 {@code cn.stars.*}。
 *
 * <p>由于 {@code org.lwjgl.*} 由 App 加载器定义、{@code cn.stars.*} 由 LCC 定义，
 * App 加载器在解析 {@code Display} 里的 {@code ImeFix} 引用时会从自己的 classpath
 * （游戏 jar 也在系统 classpath 上）再定义一份 {@code ImeFix}。于是：
 * <ul>
 *   <li>{@code Display} → APP 版 {@code ImeFix.replay()} → 把输入法组字串写进 APP 版
 *       {@code gameComposition}；</li>
 *   <li>{@code GuiTextField} → LCC 版 {@code ImeFix.preeditLayout()} → 读 LCC 版
 *       {@code gameComposition}（永远是空的）。</li>
 * </ul>
 * 又因为客户端在 {@code WM_IME_SETCONTEXT} 里关掉了系统自带的拼音框，
 * 结果就是「中文能上屏、候选框正常，但拼音不显示」。
 *
 * <p>把 {@code org.lwjgl.} 从排除表里摘掉之后只有一份类，问题消失；
 * 副作用是 {@code org.lwjgl.*} 现在也会经过 Mixin 的 transformer。
 *
 * <p>必须在任何 {@code org.lwjgl.*} 类被加载之前调用（{@code injectIntoClassLoader}
 * 是最早的安全点）。可用 {@code -Dlq.keepLwjglInParent=true} 退回默认行为。
 */
final class LwjglClassLoader {

    private static final String KEEP_IN_PARENT = "lq.keepLwjglInParent";

    private LwjglClassLoader() {
    }

    static void unify(LaunchClassLoader classLoader) {
        if (Boolean.getBoolean(KEEP_IN_PARENT)) {
            log("[lq-loader] " + KEEP_IN_PARENT + "=true，org.lwjgl.* 仍由父加载器加载");
            return;
        }
        try {
            Field field = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
            field.setAccessible(true);
            Object raw = field.get(classLoader);
            if (!(raw instanceof Set)) {
                log("[lq-loader] classLoaderExceptions 类型异常，跳过: " + raw);
                return;
            }
            @SuppressWarnings("unchecked")
            Set<String> exceptions = (Set<String>) raw;
            if (exceptions.remove("org.lwjgl.")) {
                log("[lq-loader] org.lwjgl.* 改由 LaunchClassLoader 加载（单份类图）");
            } else {
                log("[lq-loader] org.lwjgl.* 已在父加载器排除表之外，无需调整");
            }
        } catch (Throwable t) {
            // 只是 IME 这类跨加载器引用会出问题，不阻断启动
            log("[lq-loader] 调整 org.lwjgl.* 的加载器归属失败（输入法可能异常）: " + t);
        }
    }

    private static void log(String message) {
        System.out.println(message);
        try {
            LogWrapper.info(message);
        } catch (Throwable ignored) {
            // 日志系统还没起来就只留 System.out
        }
    }
}
