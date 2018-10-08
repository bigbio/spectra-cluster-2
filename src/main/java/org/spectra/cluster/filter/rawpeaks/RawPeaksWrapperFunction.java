package org.spectra.cluster.filter.rawpeaks;

import uk.ac.ebi.pride.tools.jmzreader.model.Spectrum;

import java.util.Map;

/**
 * A wrapper to apply IRawPeakFunctions to IRawSpectrumFunctions
 */
public class RawPeaksWrapperFunction implements IRawSpectrumFunction {
    private final IRawPeakFunction peakFunction;

    public RawPeaksWrapperFunction(IRawPeakFunction peakFunction) {
        this.peakFunction = peakFunction;
    }

    @Override
    public Spectrum apply(Spectrum spectrum) {
        if (spectrum.getPeakList().size() < 1) {
            return spectrum;
        }

        Map<Double, Double> filteredPeaks = peakFunction.apply(spectrum.getPeakList());

        if (filteredPeaks == spectrum.getPeakList()) {
            return spectrum;
        }

        spectrum.getPeakList().clear();
        spectrum.getPeakList().putAll(filteredPeaks);

        return spectrum;
    }
}
