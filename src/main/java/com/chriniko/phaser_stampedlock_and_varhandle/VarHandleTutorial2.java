package com.chriniko.phaser_stampedlock_and_varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

public class VarHandleTutorial2 {

    public static void main(String[] args) {
        FieldReadingTest.test();
    }

    // ---

    static class FieldReading {

        private int val;

        public void reset() {
            val = ThreadLocalRandom.current().nextInt(1, 42);
        }

        public void increaseBy50Percent() {
            val *= 1.5;
        }

        public void increaseBy50PercentVarHandle() {
            int current = (int) VAL.get(this);
            current *= 1.5;
            VAL.set(this, current);
        }

        public void increaseBy50PercentReflection() {
            increaseBy50PercentReflection(VAL_FIELD);
        }

        public void increaseBy50PercentReflectionAccessible() {
            increaseBy50PercentReflection(VAL_FIELD_ACCESSIBLE);
        }

        private void increaseBy50PercentReflection(Field field) {
            try {
                int current = field.getInt(this);
                current *= 1.5;
                field.set(this, current);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private static final VarHandle VAL;
        private static final Field VAL_FIELD;
        private static final Field VAL_FIELD_ACCESSIBLE;

        static {
            try {
                VAL = MethodHandles.lookup().findVarHandle(FieldReading.class, "val", int.class);

                VAL_FIELD = FieldReading.class.getDeclaredField("val");

                VAL_FIELD_ACCESSIBLE = FieldReading.class.getDeclaredField("val");
                VAL_FIELD_ACCESSIBLE.setAccessible(true);

            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }
    }

    // ---

    static class FieldReadingTest {

        public static void test() {
            FieldReading fr = new FieldReading();
            test(fr, "normal", fr::increaseBy50Percent);
            test(fr, "VarHandle", fr::increaseBy50PercentVarHandle);
            test(fr, "Reflection", fr::increaseBy50PercentReflection);
            test(fr, "Reflection Accessible", fr::increaseBy50PercentReflectionAccessible);
        }

        private static void test(FieldReading fr, String description, Runnable increase) {
            long time = System.nanoTime();
            try {
                for (int i = 0; i < 1_000; i++) {
                    fr.reset();
                    for (int j = 0; j < 100_000; j++) {
                        increase.run();
                    }
                }
            } finally {
                time = System.nanoTime() - time;
                System.out.printf("%s field get and set time = %dms%n", description, (time / 1_000_000));
            }
        }

    }


}
