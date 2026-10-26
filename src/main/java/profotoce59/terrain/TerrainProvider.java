package profotoce59.terrain;

public class TerrainProvider {
    public static final int CONTINENTALNESS = 0;
    public static final int EROSION = 1;
    public static final int RIDGES = 2; // peaks and valleys
    public static final int WEIRDNESS = 3;

    public static CubicSpline overworldOffset() {
        CubicSpline cubicSpline = buildErosionOffsetSpline(-0.15f, 0.0f, 0.0f, 0.1f, 0.0f, -0.03f, false, false);
        CubicSpline cubicSpline2 = buildErosionOffsetSpline(-0.1f, 0.03f, 0.1f, 0.1f, 0.01f, -0.03f, false, false);
        CubicSpline cubicSpline3 = buildErosionOffsetSpline(-0.1f, 0.03f, 0.1f, 0.7f, 0.01f, -0.03f, true, true);
        CubicSpline cubicSpline4 = buildErosionOffsetSpline(-0.05f, 0.03f, 0.1f, 1.0f, 0.01f, 0.01f, true, true);
        return CubicSpline.builder(CONTINENTALNESS)
                .addPoint(-1.1f, 0.044f)
                .addPoint(-1.02f, -0.2222f)
                .addPoint(-0.51f, -0.2222f)
                .addPoint(-0.44f, -0.12f)
                .addPoint(-0.18f, -0.12f)
                .addPoint(-0.16f, cubicSpline)
                .addPoint(-0.15f, cubicSpline)
                .addPoint(-0.1f, cubicSpline2)
                .addPoint(0.25f, cubicSpline3)
                .addPoint(1.0f, cubicSpline4)
                .build();
    }

    public static CubicSpline overworldFactor() {
        return CubicSpline.builder(CONTINENTALNESS)
                .addPoint(-0.19f, 3.95f)
                .addPoint(-0.15f, getErosionFactor(6.25f, true))
                .addPoint(-0.1f, getErosionFactor(5.47f, true))
                .addPoint(0.03f, getErosionFactor(5.08f, true))
                .addPoint(0.06f, getErosionFactor(4.69f, false))
                .build();
    }

    private static CubicSpline getErosionFactor(float f, boolean bl) {
        CubicSpline cubicSpline = CubicSpline.builder(RIDGES).addPoint(-0.2f, 6.3f).addPoint(0.2f, f).build();
        CubicSpline.Builder builder = CubicSpline.builder(EROSION)
                .addPoint(-0.6f, cubicSpline)
                .addPoint(-0.5f, CubicSpline.builder(RIDGES).addPoint(-0.05f, 6.3f).addPoint(0.05f, 2.67f).build())
                .addPoint(-0.35f, cubicSpline)
                .addPoint(-0.25f, cubicSpline)
                .addPoint(-0.1f, CubicSpline.builder(RIDGES).addPoint(-0.05f, 2.67f).addPoint(0.05f, 6.3f).build())
                .addPoint(0.03f, cubicSpline);
        if (bl) {
            CubicSpline cubicSpline2 = CubicSpline.builder(RIDGES).addPoint(0.0f, f).addPoint(0.1f, 0.625f).build();
            CubicSpline cubicSpline3 = CubicSpline.builder(WEIRDNESS).addPoint(-0.9f, f).addPoint(-0.69f, cubicSpline2).build();
            builder.addPoint(0.35f, f).addPoint(0.45f, cubicSpline3).addPoint(0.55f, cubicSpline3).addPoint(0.62f, f);
        } else {
            CubicSpline cubicSpline4 = CubicSpline.builder(WEIRDNESS).addPoint(-0.7f, cubicSpline).addPoint(-0.15f, 1.37f).build();
            CubicSpline cubicSpline5 = CubicSpline.builder(WEIRDNESS).addPoint(0.45f, cubicSpline).addPoint(0.7f, 1.56f).build();
            builder.addPoint(0.05f, cubicSpline5).addPoint(0.4f, cubicSpline5).addPoint(0.45f, cubicSpline4).addPoint(0.55f, cubicSpline4).addPoint(0.58f, f);
        }
        return builder.build();
    }

    private static CubicSpline buildErosionOffsetSpline(float f, float f2, float f3, float f4, float f5, float f6, boolean bl, boolean bl2) {
        CubicSpline cubicSpline = buildMountainRidgeSplineWithPoints(lerp(f4, 0.6f, 1.5f), bl2);
        CubicSpline cubicSpline2 = buildMountainRidgeSplineWithPoints(lerp(f4, 0.6f, 1.0f), bl2);
        CubicSpline cubicSpline3 = buildMountainRidgeSplineWithPoints(f4, bl2);
        CubicSpline cubicSpline4 = ridgeSpline(f - 0.15f, 0.5f * f4, lerp(0.5f, 0.5f, 0.5f) * f4, 0.5f * f4, 0.6f * f4, 0.5f);
        CubicSpline cubicSpline5 = ridgeSpline(f, f5 * f4, f2 * f4, 0.5f * f4, 0.6f * f4, 0.5f);
        CubicSpline cubicSpline6 = ridgeSpline(f, f5, f5, f2, f3, 0.5f);
        CubicSpline cubicSpline7 = ridgeSpline(f, f5, f5, f2, f3, 0.5f);
        CubicSpline cubicSpline8 = CubicSpline.builder(RIDGES).addPoint(-1.0f, f).addPoint(-0.4f, cubicSpline6).addPoint(0.0f, f3 + 0.07f).build();
        CubicSpline cubicSpline9 = ridgeSpline(-0.02f, f6, f6, f2, f3, 0.0f);
        CubicSpline.Builder builder = CubicSpline.builder(EROSION)
                .addPoint(-0.85f, cubicSpline)
                .addPoint(-0.7f, cubicSpline2)
                .addPoint(-0.4f, cubicSpline3)
                .addPoint(-0.35f, cubicSpline4)
                .addPoint(-0.1f, cubicSpline5)
                .addPoint(0.2f, cubicSpline6);
        if (bl) {
            builder.addPoint(0.4f, cubicSpline7).addPoint(0.45f, cubicSpline8).addPoint(0.55f, cubicSpline8).addPoint(0.58f, cubicSpline7);
        }
        builder.addPoint(0.7f, cubicSpline9);
        return builder.build();
    }

    private static CubicSpline buildMountainRidgeSplineWithPoints(float f, boolean bl) {
        CubicSpline.Builder builder = CubicSpline.builder(RIDGES);
        float f4 = mountainContinentalness(-1.0f, f, -0.7f);
        float f6 = mountainContinentalness(1.0f, f, -0.7f);
        float f7 = calculateMountainRidgeZeroContinentalnessPoint(f);
        if (-0.65f < f7 && f7 < 1.0f) {
            float f9 = mountainContinentalness(-0.65f, f, -0.7f);
            float f11 = mountainContinentalness(-0.75f, f, -0.7f);
            float f12 = calculateSlope(f4, f11, -1.0f, -0.75f);
            builder.addPoint(-1.0f, f4, f12);
            builder.addPoint(-0.75f, f11);
            builder.addPoint(-0.65f, f9);
            float f13 = mountainContinentalness(f7, f, -0.7f);
            float f14 = calculateSlope(f13, f6, f7, 1.0f);
            builder.addPoint(f7 - 0.01f, f13);
            builder.addPoint(f7, f13, f14);
            builder.addPoint(1.0f, f6, f14);
        } else {
            float f16 = calculateSlope(f4, f6, -1.0f, 1.0f);
            if (bl) {
                builder.addPoint(-1.0f, Math.max(0.2f, f4));
                builder.addPoint(0.0f, lerp(0.5f, f4, f6), f16);
            } else {
                builder.addPoint(-1.0f, f4, f16);
            }
            builder.addPoint(1.0f, f6, f16);
        }
        return builder.build();
    }

    private static CubicSpline ridgeSpline(float f, float f2, float f3, float f4, float f5, float f6) {
        float f7 = Math.max(0.5f * (f2 - f), f6);
        float f8 = 5.0f * (f3 - f2);
        return CubicSpline.builder(RIDGES)
                .addPoint(-1.0f, f, f7)
                .addPoint(-0.4f, f2, Math.min(f7, f8))
                .addPoint(0.0f, f3, f8)
                .addPoint(0.4f, f4, 2.0f * (f4 - f3))
                .addPoint(1.0f, f5, 0.7f * (f5 - f4))
                .build();
    }

    private static float mountainContinentalness(float f, float f2, float f3) {
        float f6 = 1.0f - (1.0f - f2) * 0.5f;
        float f7 = 0.5f * (1.0f - f2);
        float f8 = (f + 1.17f) * 0.46082947f;
        float f9 = f8 * f6 - f7;
        if (f < f3) {
            return Math.max(f9, -0.2222f);
        }
        return Math.max(f9, 0.0f);
    }

    private static float calculateMountainRidgeZeroContinentalnessPoint(float f) {
        float f4 = 1.0f - (1.0f - f) * 0.5f;
        float f5 = 0.5f * (1.0f - f);
        return f5 / (0.46082947f * f4) - 1.17f;
    }

    private static float calculateSlope(float f, float f2, float f3, float f4) {
        return (f2 - f) / (f4 - f3);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
