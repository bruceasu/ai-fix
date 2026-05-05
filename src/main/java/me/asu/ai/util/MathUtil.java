package me.asu.ai.util;

public final class MathUtil {
    private MathUtil() {}

    public static double avg(double sum, long n) {
        return n <= 0 ? 0.0 : sum / n;
    }

    public static double ratio(long pos, long n) {
        return n <= 0 ? 0.0 : (double) pos / n;
    }

    

    /**
     * 安全均值：n<=0 返回 0。
     */
    public static double avg(double sum, double n) {
        return n <= 0 ? 0.0 : sum / n;
    }

    /**
     * 安全比例：n<=0 返回 0。
     */
    public static double ratio(long pos, double n) {
        return n <= 0 ? 0.0 : ((double) pos) / n;
    }


    /**
     * 计算向量 L2 范数。
     */
    public static double l2(double[] v) {
        double s = 0;
        for (double x : v) s += x * x;
        return Math.sqrt(s);
    }


    /**
     * 计算 z-score；std 为 0 时返回 0 防止除零。
     */
    public static double zscore(double accountMean, double globalMean, double globalStd) {
        if (globalStd == 0) return 0;
        return (accountMean - globalMean) / globalStd;
    }


    /**
     * 基于 long n 的均值计算。
     */
    public static double mean(double sum, long n) {
        return n == 0 ? 0 : sum / n;
    }

    /**
     * 基于 long n 的标准差计算。
     */
   public static double std(double sum, double sumSq, long n) {
        if (n <= 1) return 0;
        double m = sum / n;
        double variance = (sumSq / n) - m * m;
        return Math.sqrt(Math.max(0.0, variance));
    }

    /**
     * 基于 sum/sumSq/n 计算标准差（double 计数版）。
     */
    public static double std(double sum, double sumSq, double n) {
        if (n <= 1) return 0;
        double mean = sum / n;
        double variance = (sumSq / n) - mean * mean;
        return Math.sqrt(Math.max(0.0, variance));
    }

    public static double sampleStd(double sum, double sumSq, long n) {
        if (n <= 1) return 0;
        double variance = (sumSq - (sum * sum) / n) / (n - 1);
        return Math.sqrt(Math.max(0.0, variance));
    }

    /**
     * 在已排序数组上计算分位数（线性插值）。
     * 算法：把 p 映射到 [0, n-1] 的实数下标 idx，取邻近 lo/hi 两点按比例插值。
     */
    public static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        if (p <= 0) return sorted[0];
        if (p >= 1) return sorted[sorted.length - 1];
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        double frac = idx - lo;
        return (long) Math.round(sorted[lo] * (1 - frac) + sorted[hi] * frac);
    }
}
