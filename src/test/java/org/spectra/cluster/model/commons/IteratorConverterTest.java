package org.spectra.cluster.model.commons;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;

public class IteratorConverterTest {

    Iterator<Integer> it = Collections.singleton(1).iterator();

    @Test
    public void hasNext() {
        Assert.assertTrue(it.hasNext());
    }

    @Test
    public void next() {
        Assert.assertEquals(1, (int) it.next());
    }
}