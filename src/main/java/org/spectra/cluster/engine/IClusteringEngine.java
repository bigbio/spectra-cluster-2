package org.spectra.cluster.engine;

import org.spectra.cluster.model.cluster.ICluster;
import org.spectra.cluster.model.spectra.IBinarySpectrum;

/**
 * An IClusteringEngine is the class the performs the actual clustering. It takes IBinarySpectra
 * as input and returns the final ICluster objects.
 *
 * @author jg
 */
public interface IClusteringEngine {
    /**
     * Cluster the spectra.
     * @param spectra The IBinarySpectra to cluster
     * @return The clustering result as an array of ICluster
     */
    ICluster[] clusterSpectra(IBinarySpectrum... spectra);

    /**
     * Clusters the spectrum in an incremental way. Spectra must be sorted according
     * to precursor m/z. Ie. only spectra with a higher precursor m/z can be incrementally
     * added to be processed.
     * @param spectrum The spectrum to add (precursor m/z must be higher than the last spectrum).
     * @return The subset of clusters that falls below the set precursor tolerance of the last added spectrum.
     */
    // @Deprecated // I'm currently a little unsure whether we should still support this use case.
    // ICluster[] clusterSpectrumIncrementally(IBinarySpectrum spectrum);

    /**
     * Returns the defined precursor tolerance.
     * @return The precursor tolerance in integer space.
     */
    int getPrecursorTolerance();
}