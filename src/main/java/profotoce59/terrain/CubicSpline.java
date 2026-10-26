package profotoce59.terrain;

import java.util.ArrayList;
import java.util.List;

public interface CubicSpline {
    float apply(float[] params);

    static CubicSpline constant(float val) {
        return params -> val;
    }

    static Builder builder(int coordinateIndex) {
        return new Builder(coordinateIndex);
    }

    class Builder {
        private final int coordinateIndex;
        private final List<Float> locations = new ArrayList<>();
        private final List<CubicSpline> values = new ArrayList<>();
        private final List<Float> derivatives = new ArrayList<>();

        public Builder(int coordinateIndex) {
            this.coordinateIndex = coordinateIndex;
        }

        public Builder addPoint(float location, float value) {
            return addPoint(location, CubicSpline.constant(value), 0.0f);
        }

        public Builder addPoint(float location, float value, float derivative) {
            return addPoint(location, CubicSpline.constant(value), derivative);
        }

        public Builder addPoint(float location, CubicSpline value) {
            return addPoint(location, value, 0.0f);
        }

        public Builder addPoint(float location, CubicSpline value, float derivative) {
            this.locations.add(location);
            this.values.add(value);
            this.derivatives.add(derivative);
            return this;
        }

        public CubicSpline build() {
            float[] locs = new float[locations.size()];
            float[] ders = new float[derivatives.size()];
            for (int i = 0; i < locs.length; i++) {
                locs[i] = locations.get(i);
                ders[i] = derivatives.get(i);
            }
            return new Multipoint(coordinateIndex, locs, values.toArray(new CubicSpline[0]), ders);
        }
    }

    class Multipoint implements CubicSpline {
        private final int coordinateIndex;
        private final float[] locations;
        private final CubicSpline[] values;
        private final float[] derivatives;

        public Multipoint(int coordinateIndex, float[] locations, CubicSpline[] values, float[] derivatives) {
            this.coordinateIndex = coordinateIndex;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
        }

        @Override
        public float apply(float[] params) {
            float f = params[coordinateIndex];
            int n = findIntervalStart(locations, f);
            int last = locations.length - 1;

            if (n < 0) {
                return linearExtend(f, locations[0], values[0].apply(params), derivatives[0]);
            }
            if (n >= last) {
                return linearExtend(f, locations[last], values[last].apply(params), derivatives[last]);
            }

            float f2 = locations[n];
            float f3 = locations[n + 1];
            float f4 = (f - f2) / (f3 - f2);
            float f7 = values[n].apply(params);
            float f8 = values[n + 1].apply(params);
            float f5 = derivatives[n];
            float f6 = derivatives[n + 1];
            float f9 = f5 * (f3 - f2) - (f8 - f7);
            float f10 = -f6 * (f3 - f2) + (f8 - f7);
            return lerp(f4, f7, f8) + f4 * (1.0f - f4) * lerp(f4, f9, f10);
        }

        private static int findIntervalStart(float[] array, float val) {
            int low = 0;
            int high = array.length;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (val < array[mid]) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return low - 1;
        }

        private static float linearExtend(float f, float loc, float val, float der) {
            if (der == 0.0f) return val;
            return val + der * (f - loc);
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }
    }
}
