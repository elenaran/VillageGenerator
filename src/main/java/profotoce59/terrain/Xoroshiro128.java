package profotoce59.terrain;

public class Xoroshiro128 {
    private long seedLo;
    private long seedHi;

    public Xoroshiro128(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L;
            this.seedHi = 7640891576956012809L;
        }
    }

    public static Xoroshiro128 createFromWorldSeed(long worldSeed) {
        long l2 = worldSeed ^ 0x6A09E667F3BCC909L;
        long l3 = l2 + -7046029254386353131L;
        return new Xoroshiro128(mixStafford13(l2), mixStafford13(l3));
    }

    public static long mixStafford13(long l) {
        l = (l ^ (l >>> 30)) * -4658895280553007687L;
        l = (l ^ (l >>> 27)) * -7723592293110705685L;
        return l ^ (l >>> 31);
    }

    public long nextLong() {
        long l = this.seedLo;
        long l2 = this.seedHi;
        long l3 = Long.rotateLeft(l + l2, 17) + l;
        this.seedLo = Long.rotateLeft(l, 49) ^ (l2 ^= l) ^ (l2 << 21);
        this.seedHi = Long.rotateLeft(l2, 28);
        return l3;
    }

    public int nextInt(int n) {
        if (n <= 0) throw new IllegalArgumentException("Bound must be positive");
        long l = Integer.toUnsignedLong((int) this.nextLong());
        long l2 = l * (long) n;
        long l3 = l2 & 0xFFFFFFFFL;
        if (l3 < (long) n) {
            int n2 = Integer.remainderUnsigned(~n + 1, n);
            while (l3 < (long) n2) {
                l = Integer.toUnsignedLong((int) this.nextLong());
                l2 = l * (long) n;
                l3 = l2 & 0xFFFFFFFFL;
            }
        }
        return (int) (l2 >> 32);
    }

    public double nextDouble() {
        return (double) (this.nextLong() >>> 11) * 1.1102230246251565E-16;
    }
}
