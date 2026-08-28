package nay.amethyst.check.combat;

public final class MacroCheck {
    private static final int MIN_SAMPLES = 30;
    private static final int ANALYSIS_WINDOW = 100;
    private static final double FLAG_THRESHOLD = 6.0;
    private static final double SUSPECT_THRESHOLD = 4.0;
    private static final int SUSPECT_CONSECUTIVE = 3;

    private static final double WEIGHT_CONSISTENCY = 2.0;
    private static final double WEIGHT_GCD = 2.5;
    private static final double WEIGHT_ENTROPY = 1.5;
    private static final double WEIGHT_DUPLICATES = 1.5;
    private static final double WEIGHT_KURTOSIS = 1.0;
    private static final double WEIGHT_OUTLIER = 1.0;
    private static final double WEIGHT_COMBAT = 1.5;
    private static final double WEIGHT_BUTTERFLY = 1.0;

    private static final long GCD_TOLERANCE_NS = 2_000_000L;
    private static final long DUPLICATE_TOLERANCE_NS = 1_000_000L;
    private static final long ENTROPY_BUCKET_NS = 5_000_000L;

    public Result analyse(long[] intervals, int count, int combatClicks, int totalClicks) {
        if (count < MIN_SAMPLES) {
            return Result.INSUFFICIENT;
        }

        int len = Math.min(count, ANALYSIS_WINDOW);
        long[] sample = new long[len];
        System.arraycopy(intervals, 0, sample, 0, len);

        double consistency = scoreConsistency(sample);
        double gcd = scoreGCD(sample);
        double entropy = scoreEntropy(sample);
        double duplicates = scoreDuplicates(sample);
        double kurtosis = scoreKurtosis(sample);
        double outlier = scoreOutlierDeficit(sample);
        double combat = scoreCombatCorrelation(combatClicks, totalClicks);
        double butterfly = scoreButterfly(sample);

        double total = consistency + gcd + entropy + duplicates + kurtosis + outlier + combat + butterfly;

        StringBuilder breakdown = new StringBuilder();
        appendIfNonZero(breakdown, "cv", consistency);
        appendIfNonZero(breakdown, "gcd", gcd);
        appendIfNonZero(breakdown, "ent", entropy);
        appendIfNonZero(breakdown, "dup", duplicates);
        appendIfNonZero(breakdown, "kurt", kurtosis);
        appendIfNonZero(breakdown, "out", outlier);
        appendIfNonZero(breakdown, "combat", combat);
        appendIfNonZero(breakdown, "bfly", butterfly);

        return new Result(total >= FLAG_THRESHOLD, total >= SUSPECT_THRESHOLD, total, breakdown.toString());
    }

    private static double scoreConsistency(long[] intervals) {
        double mean = mean(intervals);
        if (mean < 1) return 0;
        double stdDev = stdDev(intervals, mean);
        double cv = stdDev / mean;
        if (cv < 0.08) return WEIGHT_CONSISTENCY;
        if (cv < 0.15) return WEIGHT_CONSISTENCY * 0.5;
        return 0;
    }

    private static double scoreGCD(long[] intervals) {
        long bestCandidate = 0;
        double bestRatio = 0;
        for (long step = 10_000_000L; step <= 200_000_000L; step += 5_000_000L) {
            int aligned = 0;
            for (long interval : intervals) {
                long remainder = interval % step;
                if (remainder <= GCD_TOLERANCE_NS || step - remainder <= GCD_TOLERANCE_NS) {
                    aligned++;
                }
            }
            double ratio = (double) aligned / intervals.length;
            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestCandidate = step;
            }
        }
        if (bestCandidate < 10_000_000L) return 0;
        if (bestRatio > 0.80) return WEIGHT_GCD;
        if (bestRatio > 0.60) return WEIGHT_GCD * 0.4;
        return 0;
    }

    private static double scoreEntropy(long[] intervals) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long v : intervals) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max - min < ENTROPY_BUCKET_NS) return WEIGHT_ENTROPY;
        int bucketCount = (int) ((max - min) / ENTROPY_BUCKET_NS) + 1;
        bucketCount = Math.min(bucketCount, 200);
        int[] buckets = new int[bucketCount];
        for (long v : intervals) {
            int idx = (int) ((v - min) / ENTROPY_BUCKET_NS);
            if (idx >= bucketCount) idx = bucketCount - 1;
            buckets[idx]++;
        }
        double entropy = 0;
        double n = intervals.length;
        for (int b : buckets) {
            if (b == 0) continue;
            double p = b / n;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        if (entropy < 1.5) return WEIGHT_ENTROPY;
        if (entropy < 2.5) return WEIGHT_ENTROPY * 0.5;
        return 0;
    }

    private static double scoreDuplicates(long[] intervals) {
        int pairs = 0;
        int duplicatePairs = 0;
        for (int i = 0; i < intervals.length - 1; i++) {
            pairs++;
            if (Math.abs(intervals[i] - intervals[i + 1]) <= DUPLICATE_TOLERANCE_NS) {
                duplicatePairs++;
            }
        }
        if (pairs == 0) return 0;
        double ratio = (double) duplicatePairs / pairs;
        if (ratio > 0.40) return WEIGHT_DUPLICATES;
        if (ratio > 0.25) return WEIGHT_DUPLICATES * 0.5;
        return 0;
    }

    private static double scoreKurtosis(long[] intervals) {
        double mean = mean(intervals);
        double std = stdDev(intervals, mean);
        if (std < 1) return WEIGHT_KURTOSIS;
        double sum = 0;
        for (long v : intervals) {
            double diff = (v - mean) / std;
            sum += diff * diff * diff * diff;
        }
        double kurtosis = (sum / intervals.length) - 3.0;
        if (kurtosis > 8.0) return WEIGHT_KURTOSIS;
        if (kurtosis > 5.0) return WEIGHT_KURTOSIS * 0.5;
        return 0;
    }

    private static double scoreOutlierDeficit(long[] intervals) {
        if (intervals.length < 50) return 0;
        double mean = mean(intervals);
        double std = stdDev(intervals, mean);
        if (std < 1) return WEIGHT_OUTLIER;
        int outliers = 0;
        for (long v : intervals) {
            if (Math.abs(v - mean) > 2 * std) outliers++;
        }
        double ratio = (double) outliers / intervals.length;
        if (ratio < 0.03) return WEIGHT_OUTLIER;
        if (ratio < 0.05) return WEIGHT_OUTLIER * 0.5;
        return 0;
    }

    private static double scoreCombatCorrelation(int combatClicks, int totalClicks) {
        if (totalClicks < 100) return 0;
        double ratio = (double) combatClicks / totalClicks;
        if (ratio > 0.95) return WEIGHT_COMBAT;
        if (ratio > 0.85) return WEIGHT_COMBAT * 0.5;
        return 0;
    }

    private static double scoreButterfly(long[] intervals) {
        if (intervals.length < 20) return 0;
        double meanEven = 0, meanOdd = 0;
        int evenCount = 0, oddCount = 0;
        for (int i = 0; i < intervals.length; i++) {
            if (i % 2 == 0) { meanEven += intervals[i]; evenCount++; }
            else { meanOdd += intervals[i]; oddCount++; }
        }
        if (evenCount == 0 || oddCount == 0) return 0;
        meanEven /= evenCount;
        meanOdd /= oddCount;

        double cov = 0, varEven = 0, varOdd = 0;
        for (int i = 0; i < intervals.length - 1; i += 2) {
            double eVal = intervals[i] - meanEven;
            double oVal = intervals[i + 1] - meanOdd;
            cov += eVal * oVal;
            varEven += eVal * eVal;
            varOdd += oVal * oVal;
        }
        if (varEven < 1 || varOdd < 1) return 0;
        double correlation = cov / Math.sqrt(varEven * varOdd);

        double meanAll = mean(intervals);
        long nsPerClick = (long) meanAll;
        double cps = nsPerClick > 0 ? 1_000_000_000.0 / nsPerClick : 0;

        if (correlation < -0.7 && cps > 12) return WEIGHT_BUTTERFLY;
        if (correlation < -0.5 && cps > 14) return WEIGHT_BUTTERFLY * 0.5;
        return 0;
    }

    private static double mean(long[] values) {
        double sum = 0;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    private static double stdDev(long[] values, double mean) {
        double sum = 0;
        for (long v : values) {
            double diff = v - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.length);
    }

    private static long approximateGCD(long[] values) {
        long result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = gcd(result, values[i]);
            if (result <= GCD_TOLERANCE_NS) return result;
        }
        return result;
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b > GCD_TOLERANCE_NS) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    private static void appendIfNonZero(StringBuilder sb, String label, double value) {
        if (value <= 0) return;
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(label).append('=').append(String.format("%.1f", value));
    }

    public record Result(boolean flagged, boolean suspect, double score, String breakdown) {
        static final Result INSUFFICIENT = new Result(false, false, 0, "");
    }
}
