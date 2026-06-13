package com.jactil.javafx.tickethelper;

import javafx.application.Application;

/**
 * 应用启动代理类
 * <p>
 * 解决 JavaFX 11+ 在 IDE 中直接运行 main 方法时报
 * "缺少 JavaFX 运行时组件" 的问题。
 * <p>
 * 原因：JavaFX 的 Application.launch() 会检查调用栈，
 * 如果 main 方法所在类继承了 Application，会要求 JavaFX 运行时在模块路径上。
 * 通过一个不继承 Application 的外部类来调用 launch()，即可绕过此检查。
 * <p>
 * 在 IDE 中运行时，请以此类作为启动入口。
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
