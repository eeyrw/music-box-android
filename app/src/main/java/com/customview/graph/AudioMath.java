package com.customview.graph;

public class AudioMath {

    private static final float DB_FLOOR = -50f;

    /**
     * 计算 PCM 数组的 RMS dBFS
     *
     * @param pcm float 数组，范围 [-1, 1]
     * @return RMS dBFS
     */
    public static float calcRmsDb(float[] pcm) {
        if (pcm == null || pcm.length == 0) return DB_FLOOR;

        double sum = 0.0;
        for (float x : pcm) {
            sum += x * x;
        }
        double rms = Math.sqrt(sum / pcm.length);
        return linToDb((float) rms);
    }

    /**
     * 计算 PCM 数组的 Peak dBFS
     *
     * @param pcm float 数组，范围 [-1, 1]
     * @return Peak dBFS
     */
    public static float calcPeakDb(float[] pcm) {
        if (pcm == null || pcm.length == 0) return DB_FLOOR;

        float peak = 0f;
        for (float x : pcm) {
            peak = Math.max(peak, Math.abs(x));
        }
        return linToDb(peak);
    }

    /**
     * 线性幅度 → dBFS
     */
    private static float linToDb(float v) {
        if (v <= 0f) return DB_FLOOR;
        return Math.max(20f * (float) Math.log10(v), DB_FLOOR);
    }
}
