# WindowAlarm Release R8/ProGuard 规则
# 保留 BuiltinRingtones 通过名称动态引用的 raw 音频资源（配合 res/raw/keep.xml）
-keepclassmembers class cn.xmeverylucky.pane.R$raw {
    public static int ring_*;
}
