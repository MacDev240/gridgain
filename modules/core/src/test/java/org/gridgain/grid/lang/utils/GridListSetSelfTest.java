/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.util.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;

/**
 * Test for {@link GridListSet}.
 */
@GridCommonTest(group = "Lang")
public class GridListSetSelfTest extends GridCommonAbstractTest {
    /**
     *
     */
    public void testUnsorted() {
        GridListSet<V1> set = new GridListSet<>();

        for (int i = 0; i < 10; i++)
            set.add(new V1(i, i));

        assertEquals(10, set.size());

        int i = 0;

        for (V1 v : set)
            assertEquals(i++, v.value());

        assertFalse(set.remove(new V1(10)));
        assertTrue(set.remove(new V1(9)));

        assertEquals(9, set.size());

        V1 old = set.addx(new V1(8, -8));

        assertNotNull(old);
        assertEquals(8, old.value());
        assertEquals(8, old.other());

        // Size should not have changed.
        assertEquals(9, set.size());

        V1 cur = set.get(new V1(7, -7));

        assertNotNull(cur);
        assertEquals(7, cur.value());
        assertEquals(7, cur.other());

        // Size should not have changed.
        assertEquals(9, set.size());

        old = set.removex(new V1(7, -7));

        assertNotNull(old);
        assertEquals(7, old.value());
        assertEquals(7, old.other());


        // Size should not have changed.
        assertEquals(8, set.size());

        assertFalse(set.contains(new V1(9, 9)));
        assertFalse(set.contains(new V1(7, 7)));
        assertFalse(set.contains(new V1(7, -7)));

        assertTrue(set.contains(new V1(8, 8)));
        assertTrue(set.contains(new V1(8, -8)));
    }

    /**
     *
     */
    public void testSortedNotStrict() {
        GridListSet<V1> set = new GridListSet<>(new Comparator<V1>() {
            @Override public int compare(V1 o1, V1 o2) {
                return o1.other() < o2.other() ? -1 : o1.other() == o2.other() ? 0 : 1;
            }
        }, false);

        for (int i = 0; i < 10; i++)
            set.add(new V1(i, i));

        assertEquals(10, set.size());

        assertFalse(set.add(new V1(1, 10)));

        V1 cur = set.addx(new V1(1, 20));

        assertEquals(cur.value(), 1);
        assertEquals(cur.other(), 1);

        assert cur == set.get(1);

        assertEquals(10, set.size());

        // Add 1.
        V1 a1 = new V1(10, -1);

        assertTrue(set.add(a1));

        assertEquals(11, set.size());

        assert set.get(0) == a1;

        // Add 2.
        V1 a2 = new V1(-1, 10);

        assertNull(set.addx(a2));

        assertEquals(12, set.size());

        assert set.get(11) == a2;

        // Remove 1.
        assertTrue(set.remove(new V1(10, 10)));

        assertEquals(11, set.size());

        assertEquals(0, set.get(0).value());

        // Remove 2.
        V1 rmv = set.removex(new V1(-1, 1));

        assertNotNull(rmv);

        assertEquals(-1, rmv.value());
        assertEquals(10, rmv.other());

        assertEquals(10, set.size());
    }

    /**
     *
     */
    public void testSortedStrict() {
        List<V2> vals = new ArrayList<>();

        for (int i = 0; i < 10; i++)
            vals.add(new V2(i, i));

        Collections.shuffle(vals);

        GridListSet<V2> set = new GridListSet<>(new Comparator<V2>() {
            @Override public int compare(V2 o1, V2 o2) {
                return o1.other() < o2.other() ? -1 : o1.other() == o2.other() ? 0 : 1;
            }
        }, true);

        for (V2 v : vals)
            assertTrue(set.add(v));

        int i = 0;

        for (V2 v : set) {
            assertEquals(v, new V2(i, i));

            i++;
        }

        i = 0;

        for (V2 v : set) {
            V2 cur = set.addx(new V2(i, i));

            assert v == cur;

            i++;
        }

        set.clear();

        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    /**
     *
     */
    private static class V1 {
        /** */
        private final int val;

        /** */
        private final int other;

        /**
         * @param val Value.
         */
        private V1(int val) {
            this.val = val;

            other = 0;
        }

        /**
         * @param val Value.
         * @param other Other.
         */
        private V1(int val, int other) {
            this.val = val;
            this.other = other;
        }

        /**
         * @return Value.
         */
        int value() {
            return val;
        }

        /**
         * @return Other.
         */
        int other() {
            return other;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return val;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            V1 v = (V1)o;

            return v.val == val;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(V1.class, this);
        }
    }

    /**
     *
     */
    private static class V2 extends V1 {
        /**
         * @param val Value.
         */
        private V2(int val) {
            super(val);
        }

        /**
         * @param val Value.
         * @param other Other.
         */
        private V2(int val, int other) {
            super(val, other);
        }


        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            V1 v = (V1)o;

            return v.val == value() && v.other == other();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(V2.class, this, super.toString());
        }
    }
}