package me.asu.ai.analyze;

public final class AnalyzePromptBuilder {

    private AnalyzePromptBuilder() {
    }

    public static String build(AnalyzeInputType type, String input) {
        String sanitizedInput = input == null ? "" : input;
        return switch (type) {
            case JAVA_EXCEPTION -> """
                    你是资深 Java 工程师。
                    请分析下面的异常，并输出：
                    1. 错误类型
                    2. 可能原因（最多 3 个）
                    3. 具体定位（文件和行号，如果有）
                    4. 修复建议（必须具体）

                    要求：
                    - 不要泛泛而谈
                    - 输出必须结构化
                    - 不超过 200 字
                    - 不执行任何代码修改或部署操作
                    - 严格使用下面格式：
                    【问题类型】
                    ...
                    【可能原因】
                    1. ...
                    2. ...
                    【定位】
                    ...
                    【建议】
                    1. ...
                    2. ...

                    输入：
                    %s
                    """.formatted(sanitizedInput);
            case TEST_OUTPUT -> """
                    你是测试工程专家。
                    请分析下面的测试输出，并输出：
                    1. 失败测试
                    2. 失败原因
                    3. 修复建议

                    要求：
                    - 提供具体修复方向
                    - 如果是代码问题，给出示例
                    - 不执行任何代码修改或部署操作
                    - 严格使用下面格式：
                    【问题类型】
                    ...
                    【可能原因】
                    1. ...
                    2. ...
                    【定位】
                    ...
                    【建议】
                    1. ...
                    2. ...

                    输入：
                    %s
                    """.formatted(sanitizedInput);
            case LOG, UNKNOWN -> """
                    你是资深 SRE。
                    请分析下面的日志，并输出：
                    1. 高频错误
                    2. 是否存在异常时间点
                    3. 可能根因（最多 3 个）
                    4. 排查建议

                    要求：
                    - 输出简洁
                    - 避免重复日志内容
                    - 不执行任何代码修改或部署操作
                    - 严格使用下面格式：
                    【问题类型】
                    ...
                    【可能原因】
                    1. ...
                    2. ...
                    【定位】
                    ...
                    【建议】
                    1. ...
                    2. ...

                    输入：
                    %s
                    """.formatted(sanitizedInput);
        };
    }

    public static String buildUnknownFallback() {
        return """
                【问题类型】
                未知
                【可能原因】
                1. 输入信息不足，无法稳定判断类型
                【定位】
                无法定位
                【建议】
                1. 请提供更多上下文，例如完整日志或错误堆栈
                """;
    }
}
