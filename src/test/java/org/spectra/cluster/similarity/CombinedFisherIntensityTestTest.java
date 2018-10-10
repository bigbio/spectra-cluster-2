package org.spectra.cluster.similarity;

import cern.jet.random.HyperGeometric;
import cern.jet.random.engine.RandomEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spectra.cluster.cdf.MinNumberComparisonsAssessor;
import org.spectra.cluster.engine.GreedyClusteringEngine;
import org.spectra.cluster.filter.binaryspectrum.HighestIntensityNPeaksFunction;
import org.spectra.cluster.filter.binaryspectrum.HighestPeakPerBinFunction;
import org.spectra.cluster.filter.binaryspectrum.IBinarySpectrumFunction;
import org.spectra.cluster.filter.rawpeaks.KeepNHighestRawPeaks;
import org.spectra.cluster.filter.rawpeaks.RawPeaksWrapperFunction;
import org.spectra.cluster.filter.rawpeaks.RemoveImpossiblyHighPeaksFunction;
import org.spectra.cluster.filter.rawpeaks.RemovePrecursorPeaksFunction;
import org.spectra.cluster.io.IPropertyStorage;
import org.spectra.cluster.io.InMemoryPropertyStorage;
import org.spectra.cluster.io.MzSpectraReader;
import org.spectra.cluster.model.cluster.ICluster;
import org.spectra.cluster.model.spectra.BinaryPeak;
import org.spectra.cluster.model.spectra.BinarySpectrum;
import org.spectra.cluster.model.spectra.IBinarySpectrum;
import org.spectra.cluster.normalizer.BasicIntegerNormalizer;
import org.spectra.cluster.normalizer.MaxPeakNormalizer;
import org.spectra.cluster.normalizer.TideBinner;
import uk.ac.ebi.pride.tools.jmzreader.model.Spectrum;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CombinedFisherIntensityTestTest {
    private List<IBinarySpectrum> impSpectra;
    private IPropertyStorage storage = new InMemoryPropertyStorage();

    @Before
    public void setUp() throws Exception {
        File impFile = new File(getClass().getClassLoader().getResource("imp_single_cluster.mgf").toURI());
        MzSpectraReader reader = new MzSpectraReader(impFile, new TideBinner(), new MaxPeakNormalizer(),
                new BasicIntegerNormalizer(), new HighestPeakPerBinFunction(),
                new RemoveImpossiblyHighPeaksFunction()
                    .specAndThen(new RemovePrecursorPeaksFunction(0.5))
                    .specAndThen(new RawPeaksWrapperFunction(new KeepNHighestRawPeaks(70))));

        Iterator<IBinarySpectrum> spectrumIterator = reader.readBinarySpectraIterator(storage);
        impSpectra = new ArrayList<>(50);

        while (spectrumIterator.hasNext()) {
            impSpectra.add(spectrumIterator.next());
        }
    }

    @Test
    public void testScoreGeneration() throws Exception {
        // read the original scores
        URI uri = Objects.requireNonNull(BinarySpectrum.class.getClassLoader().getResource("same_sequence_cluster_scores.tsv")).toURI();
        Stream <String> scoreLineStream = Files.lines(Paths.get(uri));
        List<Double> scores = scoreLineStream.map(Double::new).collect(Collectors.toList());

        // get the spectra
        File peakList = new File(Objects.requireNonNull(CombinedFisherIntensityTestTest.class.getClassLoader().getResource("same_sequence_cluster.mgf")).toURI());
        MzSpectraReader reader = new MzSpectraReader(peakList);
        Iterator<IBinarySpectrum> spectrumIterator = reader.readBinarySpectraIterator();
        List<IBinarySpectrum> allSpectra = new ArrayList<>();

        while (spectrumIterator.hasNext()) {
            IBinarySpectrum s = spectrumIterator.next();

            // sort the peaks
            Arrays.parallelSort(s.getPeaks(), Comparator.comparingInt(BinaryPeak::getMz));

            allSpectra.add(s);
        }

        IBinarySpectrumSimilarity similarity = new CombinedFisherIntensityTest();

        IBinarySpectrum s1 = allSpectra.get(0);
        IBinarySpectrumFunction peakFilter = new HighestIntensityNPeaksFunction(20);
        s1 = peakFilter.apply(s1);

        // do the comparison
        for (int i = 1; i < allSpectra.size(); i++) {
            IBinarySpectrum s2 = peakFilter.apply(allSpectra.get(i));

            double score = similarity.correlation(s1, s2);
            double orgScore = scores.get(i - 1);

            if (orgScore == Double.POSITIVE_INFINITY) {
                continue;
            }

            Assert.assertFalse(String.format("Comparison %d failed", i), Double.isNaN(score));
            // score differences are caused by
            // 1) binning and the thereby caused different number of peaks and different fragment tolerance
            // 2) different intensity normalisation in the original spectra-cluster code
            Assert.assertEquals(orgScore, score, 5);

        }
    }

    @Test
    public void testHgt() {
        // Fails: minBin = 134, maxBin = 1789, peaks1 = 87, peaks2 = 383, shared = 62
        double score = new HyperGeometric(1655, 383, 87, RandomEngine.makeDefault()).pdf(62);

        Assert.assertFalse(Double.isNaN(score));
        System.out.println(String.valueOf(score));
    }

    @Test
    public void testImpCluster() throws Exception {
        IBinarySpectrum firstSpec = impSpectra.get(0);
        IBinarySpectrumSimilarity similarity = new CombinedFisherIntensityTest();
        List<Double> scores = new ArrayList<>(impSpectra.size() - 1);

        System.out.printf("Spec m/z %.2f - %.2f\n",
                impSpectra.stream().mapToDouble(s -> s.getPrecursorMz()).min().getAsDouble(),
                impSpectra.stream().mapToDouble(s -> s.getPrecursorMz()).max().getAsDouble());

        for (int i = 1; i < impSpectra.size(); i++) {
            double score = similarity.correlation(firstSpec, impSpectra.get(i));
            Assert.assertNotNull(score);
            // only accept very high scores
            Assert.assertTrue(score > 100);
            scores.add(score);
        }

        System.out.printf("Scores between %.2f - %.2f\n",
                scores.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
                scores.stream().mapToDouble(Double::doubleValue).max().getAsDouble());

        // perform the clustering
        GreedyClusteringEngine engine = new GreedyClusteringEngine(BasicIntegerNormalizer.MZ_CONSTANT,
                1, 0.99f, 5,
                similarity, new MinNumberComparisonsAssessor(10_000), 5);

        ICluster[] clusters = engine.clusterSpectra(impSpectra.toArray(new IBinarySpectrum[0]));

        Assert.assertEquals(1, clusters.length);
    }
}
