package com.chriniko.phaser_stampedlock_and_varhandle;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAccumulator;

public class VarHandleTutorial {

    private static final ThreadMXBean tmbean = ManagementFactory.getThreadMXBean();

    private static final int REPEATS = 10;

    private static LongAccumulator bestMoveThread = new LongAccumulator(Long::max, 0);
    private static LongAccumulator bestDistanceThread = new LongAccumulator(Long::max, 0);

    private static LongAccumulator worstMoveThread = new LongAccumulator(Long::min, Long.MAX_VALUE);
    private static LongAccumulator worstDistanceThread = new LongAccumulator(Long::min, Long.MAX_VALUE);

    public static void main(String[] args) throws InterruptedException {

        System.out.println("c/e = cpu time / elapsed time");
        System.out.println("s/e = system cpu time / elapsed time");
        System.out.println("u/e = user cpu time / elapsed time");

        for (int i = 0; i < REPEATS; i++) {
            new PositionTest().test();
        }

        System.out.println();
        System.out.println("Best values:");
        System.out.printf(Locale.US, "\tmoveBy()        %,d%n", bestMoveThread.longValue());
        System.out.printf(Locale.US, "\tdistanceFromOrigin()         %,d%n", bestDistanceThread.longValue());
        System.out.println("Worst values:");
        System.out.printf(Locale.US, "\tmoveBy()        %,d%n", worstMoveThread.longValue());
        System.out.printf(Locale.US, "\tdistanceFromOrigin()         %,d%n", worstDistanceThread.longValue());

    }


    // ---

    static class Position {

        private volatile double[] xy = new double[2];
        private final long xyMemoryOffset;

        public Position(double x, double y) {
            xy[0] = x;
            xy[1] = y;

            try {
                xyMemoryOffset = UNSAFE.objectFieldOffset(Position.class.getDeclaredField("xy"));
            } catch (NoSuchFieldException e) {
                throw new Error(e);
            }
        }

        public void moveBy(double deltaX, double deltaY) {
            var current = xy;
            var next = new double[]{0, 0};
            do {
                next[0] = current[0] + deltaX;
                next[1] = current[1] + deltaY;
            } while (!XY.compareAndSet(this, current, next));
        }

        public void moveByUsingUnsafe(double deltaX, double deltaY) {
            var current = xy;
            var next = new double[2];
            do {
                next[0] = current[0] + deltaX;
                next[1] = current[1] + deltaY;
            } while (!UNSAFE.compareAndSwapObject(this, xyMemoryOffset, current, next));

        }

        public double distanceFromOrigin() {
            var current = xy;
            return Math.hypot(current[0], current[1]);
        }


        private final static Unsafe UNSAFE;
        private final static VarHandle XY;

        static {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                UNSAFE = (Unsafe) f.get(null);

                XY = MethodHandles.lookup().findVarHandle(Position.class, "xy", double[].class);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }
    }

    // ---

    static class PositionTest {

        static void test() throws InterruptedException {
            Position position = new Position(0, 0);
            AtomicBoolean testing = new AtomicBoolean(true);

            Thread[] threads = {

                    new Thread(() -> {
                        double[] moves = ThreadLocalRandom.current().doubles(1024, -100, +100).toArray();

                        long time = System.currentTimeMillis();
                        long userTime = tmbean.getCurrentThreadUserTime();
                        long cpuTime = tmbean.getCurrentThreadCpuTime();

                        long count = 0;
                        int pos = 0;

                        while (testing.get()) {
                            //position.moveBy(moves[pos++ & 1023], moves[pos++ & 1023]);
                            position.moveByUsingUnsafe(moves[pos++ & 1023], moves[pos++ & 1023]);

                            count++;
                        }

                        bestMoveThread.accumulate(count);
                        worstMoveThread.accumulate(count);

                        time = System.currentTimeMillis() - time;
                        userTime = tmbean.getCurrentThreadUserTime() - userTime;
                        cpuTime = tmbean.getCurrentThreadCpuTime() - cpuTime;

                        System.out.printf(Locale.US, "move() called %,d times, c/e=%d%%, u/e=%d%%, s/e=%d%%%n",
                                count,
                                (cpuTime / time) / 10_000,
                                userTime / time / 10_000,
                                (cpuTime - userTime) / time / 10_000
                        );

                    }, "moveThread"),

                    new Thread(() -> {
                        long time = System.currentTimeMillis();
                        long userTime = tmbean.getCurrentThreadUserTime();
                        long cpuTime = tmbean.getCurrentThreadCpuTime();

                        long count = 0;
                        double totalDistance = 0;

                        while (testing.get()) {
                            totalDistance += position.distanceFromOrigin();
                            count++;
                        }
                        bestDistanceThread.accumulate(count);
                        worstDistanceThread.accumulate(count);

                        time = System.currentTimeMillis() - time;
                        userTime = tmbean.getCurrentThreadUserTime() - userTime;
                        cpuTime = tmbean.getCurrentThreadCpuTime() - cpuTime;

                        System.out.printf(Locale.US, "distanceFromOrigin() called %,d times, c/e=%d%%, u/e=%d%%, s/e=%d%%%n",
                                count,
                                (cpuTime / time) / 10_000,
                                userTime / time / 10_000,
                                (cpuTime - userTime) / time / 10_000
                        );
                    }, "distanceFromOriginThread"),

            };

            for (Thread thread : threads) {
                thread.start();
            }

            Thread.sleep(3000);
            testing.set(false);

            for (Thread thread : threads) {
                thread.interrupt();
                thread.join();
            }

        }

    }


}
