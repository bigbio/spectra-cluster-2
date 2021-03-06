package org.spectra.cluster.utils.performance;


import io.github.bigbio.pgatk.io.common.spectra.Spectrum;
import io.github.bigbio.pgatk.io.mgf.MgfIterableReader;
import org.ehcache.sizeof.SizeOf;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spectra.cluster.engine.GreedyClusteringEngine;
import org.spectra.cluster.model.spectra.BinarySpectrum;
import org.spectra.cluster.model.spectra.IBinarySpectrum;
import org.spectra.cluster.normalizer.BasicIntegerNormalizer;
import org.spectra.cluster.normalizer.FactoryNormalizer;
import org.spectra.cluster.normalizer.MaxPeakNormalizer;
import org.spectra.cluster.normalizer.TideBinner;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This Test check the size of an {@link IBinarySpectrum} List and compare with a List of {@link io.github.bigbio.pgatk.io.common.spectra.Spectrum}. The test
 * expect that the size is half of the original size of an spectrum.
 *
 * @author ypriverol
 */
public class ObjectSizeFetcherTest {

    List<Spectrum> spectrumList;
    BinarySpectrum[] binarySpectrumList;


    @Before
    public void setUp() throws Exception {

        URI uri = Objects.requireNonNull(BinarySpectrum.class.getClassLoader().getResource("single-spectra.mgf")).toURI();
        MgfIterableReader mgfFile = new MgfIterableReader(new File(uri), true, false, true);
        spectrumList = new ArrayList<>();
        binarySpectrumList = new BinarySpectrum[2];
        BasicIntegerNormalizer precursorNormalizer = new BasicIntegerNormalizer();
        FactoryNormalizer factory = new FactoryNormalizer(new TideBinner(), new MaxPeakNormalizer());
        int count = 0;
        while(mgfFile.hasNext()){
            Spectrum spec = mgfFile.next();
            spectrumList.add(spec);
            binarySpectrumList[count] = new BinarySpectrum(
                    (precursorNormalizer).binValue(spec.getPrecursorMZ()),
                    spec.getPrecursorCharge(),
                    factory.normalizePeaks(spec.getPeakList()),
                    GreedyClusteringEngine.COMPARISON_FILTER);
            count++;
        }
    }

    @Test
    public void getObjectSize() {
        SizeOf sizeOf = SizeOf.newInstance();
        long size = sizeOf.deepSizeOf(spectrumList);
        long binarySize = sizeOf.deepSizeOf(binarySpectrumList);

        Assert.assertTrue(binarySize * 2 < size);

    }

    @Test
    public void getExpectedObjectSizeMillion() {

        List<Spectrum> millionSpectra = new ArrayList<>(1000000);
        BinarySpectrum[] millionBinarySpectrum = new BinarySpectrum[1000000];
        for(int i= 0; i < 500000; i++){
            millionSpectra.add(spectrumList.get(0));
            millionSpectra.add(spectrumList.get(1));
            millionBinarySpectrum[i] = binarySpectrumList[0];
            millionBinarySpectrum[i+1] = binarySpectrumList[1];
        }

        SizeOf sizeOf = SizeOf.newInstance();
        long size = sizeOf.deepSizeOf(millionSpectra);
        long binarySize = sizeOf.deepSizeOf(millionBinarySpectrum);

        Assert.assertTrue(binarySize * 100 < size);

    }
}