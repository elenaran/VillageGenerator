package profotoce59.utils;

public class DecoratorRand {
    private long seedLo, seedHi;

    public void setSeed(long seed0, long seed1) {
        this.seedLo = seed0;
        this.seedHi = seed1;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L;
            this.seedHi = 7640891576956012809L;
        }
    }

    public static long mixStafford13(long l) {
        l = (l ^ l >>> 30) * -4658895280553007687L;
        l = (l ^ l >>> 27) * -7723592293110705685L;
        return l ^ l >>> 31;
    }

    public void setSeed(long l) {
        long l2 = l ^ 0x6A09E667F3BCC909L;
        long l3 = l2 + -7046029254386353131L;
        this.setSeed(mixStafford13(l2), mixStafford13(l3));
    }

    public long xoroNextLong() {
        long l = this.seedLo;
        long l2 = this.seedHi;
        long l3 = Long.rotateLeft(l + l2, 17) + l;
        this.seedLo = Long.rotateLeft(l, 49) ^ (l2 ^= l) ^ l2 << 21;
        this.seedHi = Long.rotateLeft(l2, 28);
        return l3;
    }

    public long nextLong() {
        int a_ = (int) (this.xoroNextLong() >> 32);
        int b = (int) (this.xoroNextLong() >> 32);
        long a = (long) a_ << 32;
        return a + (long) b;
    }

    public int nextInt(int n) {
        if (n <= 0) throw new IllegalArgumentException("Bound must be positive");
        long l = Integer.toUnsignedLong((int) this.xoroNextLong());
        long l2 = l * (long) n;
        long l3 = l2 & 0xFFFFFFFFL;
        if (l3 < (long) n) {
            int n2 = Integer.remainderUnsigned(~n + 1, n);
            while (l3 < (long) n2) {
                l = Integer.toUnsignedLong((int) this.xoroNextLong());
                l2 = l * (long) n;
                l3 = l2 & 0xFFFFFFFFL;
            }
        }
        return (int) (l2 >> 32);
    }

    public float nextFloat() {
        return (float) (this.xoroNextLong() >>> 40) * 5.9604645E-8F;
    }

    public long getPopulationSeed(long worldseed, int chunkOriginX, int chunkOriginZ) {
        this.setSeed(worldseed);
        long a = this.nextLong() | 1L;
        long b = this.nextLong() | 1L;
        return (long) chunkOriginX * a + (long) chunkOriginZ * b ^ worldseed;
    }

    public void setDecoratorSeed(long populationSeed, int index, int step) {
        long decoratorSeed = populationSeed + (long) index + (long) (10000 * step);
        this.setSeed(decoratorSeed);
    }
}
