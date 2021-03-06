package org.spectra.cluster.io.spectra;

import io.github.bigbio.pgatk.io.clustering.ClusteringFileReader;
import io.github.bigbio.pgatk.io.common.MzIterableReader;
import io.github.bigbio.pgatk.io.common.Param;
import io.github.bigbio.pgatk.io.common.PgatkIOException;
import io.github.bigbio.pgatk.io.common.spectra.Spectrum;
import io.github.bigbio.pgatk.io.mgf.MgfIterableReader;
import io.github.bigbio.pgatk.io.mgf.Ms2Query;
import io.github.bigbio.pgatk.io.objectdb.ObjectsDB;
import io.github.bigbio.pgatk.io.properties.IPropertyStorage;
import io.github.bigbio.pgatk.io.properties.StoredProperties;
import lombok.extern.slf4j.Slf4j;
import org.spectra.cluster.engine.IClusteringEngine;
import org.spectra.cluster.exceptions.SpectraClusterException;
import org.spectra.cluster.filter.binaryspectrum.HighestPeakPerBinFunction;
import org.spectra.cluster.filter.binaryspectrum.IBinarySpectrumFunction;
import org.spectra.cluster.filter.rawpeaks.*;
import org.spectra.cluster.io.cluster.ObjectDBGreedyClusterStorage;
import org.spectra.cluster.model.cluster.ICluster;
import org.spectra.cluster.model.commons.ClusterIteratorConverter;
import org.spectra.cluster.model.commons.ITuple;
import org.spectra.cluster.model.commons.SpectrumIteratorConverter;
import org.spectra.cluster.model.commons.Tuple;
import org.spectra.cluster.model.spectra.BinarySpectrum;
import org.spectra.cluster.model.spectra.IBinarySpectrum;
import org.spectra.cluster.normalizer.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *
 * Read Spectra from files into {@link org.spectra.cluster.model.spectra.BinarySpectrum} representation
 *
 *
 * @author ypriverol on 14/08/2018.
 */
@Slf4j
public class MzSpectraReader {
    private final static IRawPeakFunction top50PeaksFilter = new KeepNHighestRawPeaks(50);

    /** Pattern for validating mzML format */
    private static final Pattern mzMLHeaderPattern = Pattern.compile("^[^<]*(<\\?xml [^>]*>\\s*(<!--[^>]*-->\\s*)*)?<(mzML)|(indexedmzML) xmlns=.*", Pattern.MULTILINE);

    /** Pattern for validating mzXML format */
    private static final Pattern mzXmlHeaderPattern = Pattern.compile("^[^<]*(<\\?xml [^>]*>\\s*(<!--[^>]*-->\\s*)*)?<(mzXML) xmlns=.*", Pattern.MULTILINE);

    private final Set<ISpectrumListener> listener = new HashSet<>(5);

    private final IClusteringEngine clusteringEngine;

    private boolean clusteringFile = false;

    /** This enum type Capture the two file types supported in Spectra Cluster **/
    public enum MzFileType{

        MGF("MGF", ".mgf"),
        MZML("MZML", ".mzml"),
        MS2("MS2", ".ms2"),
        APL("APL", ".apl"),
        PKL("PKL", ".pkl"),
        DTA("DTA", ".dta"),
        CLUSTERING("CLUSTERING", ".zcl"),
        CLUSTERING_ALL("CLUSTERING_ALL", ".clustering"),
        MZXML("MZXML", "mzXML");

        private String name;
        private String extension;

        MzFileType(String name, String extension){
            this.name = name;
            this.extension = extension;
        }

        public String getName() {
            return name;
        }

        public String getExtension() {
            return extension;
        }
    }

    private Map<File, MzIterableReader> inputFiles;

    private FactoryNormalizer factory;
    /**
     * This filter is used to remove / join multiple peaks per
     * m/z window.
     */
    private IBinarySpectrumFunction peaksPerMzWindowFilter;

    private IIntegerNormalizer precursorNormalizer;

    private final IRawSpectrumFunction loadingFilter;

    private final IBinarySpectrumFunction comparisonFilter;

    /**
     * Create a Reader from a file. The file type accepted are mgf or mzml
     * @param files File to be read
     * @throws Exception File not supported
     */
    public  MzSpectraReader(IMzBinner mzBinner,
                            IIntensityNormalizer intensityBinner,
                            BasicIntegerNormalizer precursorNormalizer,
                            IBinarySpectrumFunction peaksPerMzWindowFilter,
                            IRawSpectrumFunction loadingFilter,
                            IBinarySpectrumFunction comparisonFilter,
                            IClusteringEngine clusteringEngine, File ... files ) throws Exception {
        this.inputFiles = new ConcurrentHashMap<>();
        this.clusteringEngine = clusteringEngine;

        Arrays.stream(files).parallel().forEach( file -> {
            if (!file.exists()) {
                log.error(file.toString() + " does not exist");
                return;
            }

            MzIterableReader jMzReader = null;
            try{
                Class<?> peakListclass = isValidPeakListFile(file);
                if( peakListclass != null){
                    if(peakListclass == MgfIterableReader.class){
                        jMzReader = new MgfIterableReader(file,
                                true, true, true);
                        clusteringFile = false;
                    } else if(peakListclass == ClusteringFileReader.class){
                        jMzReader = new ClusteringFileReader(file);
                        clusteringFile = true;
                    }else if( peakListclass == ObjectDBGreedyClusterStorage.class)
                        jMzReader = new ObjectDBGreedyClusterStorage(new ObjectsDB(file
                                .getAbsolutePath(), false));
//                    else if (peakListclass == AplFile.class)
//                        jMzReader = new AplFile(file);
//                    else if(peakListclass == Ms2File.class)
//                        jMzReader = new Ms2File(file);
//                    else if(peakListclass == PklFile.class)
//                        jMzReader = new PklFile(file);
//                    else if(peakListclass == DtaFile.class)
//                        jMzReader = new PklFile(file);
//                    } else if(isValidMzML(file))
//                        jMzReader = new MzMlWrapper(file);
//                    else if(isValidmzXML(file))
//                        jMzReader = new MzXMLFile(file);
                }
                }catch (PgatkIOException e){
                    String message = "The file type provided is not supported -- " +
                            Arrays.toString(MzFileType.values()) + ": " + e.getMessage();
                    log.error(message);
            }
            if(jMzReader != null)
                this.inputFiles.put(file, jMzReader);
            });

        if(this.inputFiles.isEmpty())
            throw new SpectraClusterException("Non of the provided files are supported --" + files.toString());
        this.precursorNormalizer = precursorNormalizer;
        this.peaksPerMzWindowFilter = peaksPerMzWindowFilter;
        this.factory = new FactoryNormalizer(mzBinner, intensityBinner);
        this.loadingFilter = loadingFilter;
        this.comparisonFilter = comparisonFilter;
    }

    /**
     * Create a Reader from a file. The file type accepted are mgf or mzml
     * @param file File to be read
     * @throws Exception File not supported
     */
    public  MzSpectraReader(File file, IMzBinner mzBinner,
                            IIntensityNormalizer intensityBinner,
                            BasicIntegerNormalizer precursorNormalizer,
                            IBinarySpectrumFunction peaksPerMzWindowFilter,
                            IRawSpectrumFunction loadingFilter,
                            IBinarySpectrumFunction comparisonFilter, IClusteringEngine clusteringEngine) throws Exception {


        try{
            MzIterableReader jMzReader = null;
            Class<?> peakListclass = isValidPeakListFile(file);
            if( peakListclass != null) {
                if(peakListclass == MgfIterableReader.class){
                    jMzReader = new MgfIterableReader(file,
                            true, true, true);
                    clusteringFile = false;
                } else if(peakListclass == ClusteringFileReader.class){
                    jMzReader = new ClusteringFileReader(file);
                    clusteringFile = true;
                }
//                else if (peakListclass == AplFile.class)
//                    jMzReader = new AplFile(file);
//                else if(peakListclass == Ms2File.class)
//                    jMzReader = new Ms2File(file);
//                else if(peakListclass == PklFile.class)
//                    jMzReader = new PklFile(file);
//                else if(peakListclass == DtaFile.class)
//                    jMzReader = new PklFile(file);
//            } else if(isValidMzML(file))
//                jMzReader = new MzMlWrapper(file);
//            else if(isValidmzXML(file))
//                jMzReader = new MzXMLFile(file);
            }
            if(jMzReader != null){
                this.inputFiles = new ConcurrentHashMap<>();
                this.inputFiles.put(file, jMzReader);
            }else{
                throw new SpectraClusterException("The provided file is not supported --" +
                        file.getAbsolutePath());
            }
        }catch (PgatkIOException e){
            String message = "The file type provided is not support -- " + Arrays.toString(MzFileType.values());
            log.error(message);
            throw new Exception(message);
        }

        this.precursorNormalizer = precursorNormalizer;
        this.peaksPerMzWindowFilter = peaksPerMzWindowFilter;
        this.factory = new FactoryNormalizer(mzBinner, intensityBinner);
        this.loadingFilter = loadingFilter;
        this.comparisonFilter = comparisonFilter;
        this.clusteringEngine = clusteringEngine;
    }

    /**
     * Default constructor for MzSpectraReader. This implementation uses for Normalization the following Normalizer Helpers:
     * - mz values are normalized using the {@link org.spectra.cluster.normalizer.TideBinner}.
     * - precursor mz is normalized using the {@link BasicIntegerNormalizer}.
     * - intensity values are normalized using the {@link MaxPeakNormalizer}.
     *
     * @param file Spectra file to read.
     */
    public MzSpectraReader(File file, IBinarySpectrumFunction comparisonFilter, IClusteringEngine clusteringEngine) throws Exception {
        this(file, new TideBinner(), new MaxPeakNormalizer(), new BasicIntegerNormalizer(), new HighestPeakPerBinFunction(),
                new RemoveImpossiblyHighPeaksFunction()
                // TODO: set fragment tolerance
                .specAndThen(new RemovePrecursorPeaksFunction(0.5))
                .specAndThen(new RawPeaksWrapperFunction(new KeepNHighestRawPeaks(70))),
                comparisonFilter, clusteringEngine);
    }

    public MzSpectraReader(File file, IBinarySpectrumFunction comparisonFilter) throws Exception {
        this(file, new TideBinner(), new MaxPeakNormalizer(), new BasicIntegerNormalizer(), new HighestPeakPerBinFunction(),
                new RemoveImpossiblyHighPeaksFunction()
                        // TODO: set fragment tolerance
                        .specAndThen(new RemovePrecursorPeaksFunction(0.5))
                        .specAndThen(new RawPeaksWrapperFunction(new KeepNHighestRawPeaks(70))),
                comparisonFilter, null);
    }

    /**
     * Return the iterator with the {@link IBinarySpectrum} transformed from the
     * {@link Spectrum} file.
     *
     * @return Iterator of {@link BinarySpectrum} spectra
     */
    public Iterator<IBinarySpectrum> readBinarySpectraIterator() throws SpectraClusterException {
        return readBinarySpectraIterator(null);
    }

    /**
     * Return the iterator with the {@link ICluster} transformed from the
     * {@link Spectrum} file.
     *
     * @return Iterator of {@link ICluster} spectra
     */
    public Iterator<ICluster> readClusterIterator() throws SpectraClusterException {
        return readClusterIterator(null);
    }

    /**
     * Return the iterator with the {@link IBinarySpectrum} transformed from the
     * {@link Spectrum} file.
     * @param propertyStorage If set, spectrum properties are stored in this property storage.
     *
     * @return Iterator of {@link BinarySpectrum} spectra
     */
    public ClusterIteratorConverter<Stream<ITuple>, ICluster> readClusterIterator(IPropertyStorage
                                                                                          propertyStorage)
            throws SpectraClusterException {

        Stream<Tuple<File, MzIterableReader>> iteratorStream = inputFiles
                .entrySet().stream()
                .map(x -> new Tuple<>(x.getKey(), x.getValue()))
                .collect(Collectors.toList())
                .stream();

        if(clusteringEngine == null)
            throw new SpectraClusterException("The clusterEngine should be init if you want to retrieve " +
                    "Clusters");

        return new ClusterIteratorConverter<>(iteratorStream, tupleSpectrum -> {
            // ignore clusters
            if (tupleSpectrum.getValue() instanceof io.github.bigbio.pgatk.io.common.cluster.ICluster) {
                return storeCluster(propertyStorage, tupleSpectrum);
            }
            // create the single spectrum cluster
            return clusteringEngine.createSingleSpectrumCluster(
                    peaksPerMzWindowFilter.apply(storeIBinarySpectrum(propertyStorage, tupleSpectrum)));
        });


    }

    private ICluster storeCluster(IPropertyStorage propertyStorage, ITuple tupleSpectrum) {
        File inputFile = (File) tupleSpectrum.getKey();
        io.github.bigbio.pgatk.io.common.cluster.ICluster spectrum =
                (io.github.bigbio.pgatk.io.common.cluster.ICluster) tupleSpectrum.getValue();

        ICluster s = transformIOClusterToCluster(spectrum, clusteringEngine);
//        // save additional properties
        if (propertyStorage != null) {
            // TODO: store additional cluster properties
            log.warn("Loaded cluster properties are currently not stored.");
        }

        return s;

    }

    private ICluster transformIOClusterToCluster(io.github.bigbio.pgatk.io.common.cluster.ICluster spectrum,
                                                 IClusteringEngine engine) {
        return engine.newCluster(spectrum);
    }

    /**
     * Return the iterator with the {@link IBinarySpectrum} transformed from the
     * {@link Spectrum} file.
     * @param propertyStorage If set, spectrum properties are stored in this property storage.
     *
     * @return Iterator of {@link BinarySpectrum} spectra
     */
    public SpectrumIteratorConverter<Stream<ITuple>, IBinarySpectrum> readBinarySpectraIterator(IPropertyStorage propertyStorage) throws SpectraClusterException {
        Stream<Tuple<File, MzIterableReader>> iteratorStream = inputFiles
                .entrySet().stream()
                .map(x -> new Tuple<>(x.getKey(), x.getValue()))
                .collect(Collectors.toList())
                .stream();

        if(clusteringFile)
            throw new SpectraClusterException("The clustering file do not support BinarySpectra Iterator");

        return new SpectrumIteratorConverter<>(iteratorStream, tupleSpectrum ->
                peaksPerMzWindowFilter.apply(storeIBinarySpectrum(propertyStorage, tupleSpectrum)));
    }

    /**
     * Stores the spectrum's properties in the property storage and returns
     * the binary spectrum with defined filters already applied.
     *
     * Currently, the precursor m/z is stored in integer space, the peaks are
     * normalized and the comparison filter is being applied.
     *
     * @param propertyStorage Property Storage
     * @param tupleSpectrum Spectrum Tuple
     * @return IBinarySpectrum
     */
    private IBinarySpectrum storeIBinarySpectrum(IPropertyStorage propertyStorage, ITuple tupleSpectrum) throws Exception {

        File inputFile = (File) tupleSpectrum.getKey();
        Spectrum spectrum = (Spectrum) tupleSpectrum.getValue();

        // retain the top 50 peaks for later
        Map<Double, Double> top50Peaks = top50PeaksFilter.apply(spectrum.getPeakList());

        // apply the initial loading filter
        if (loadingFilter != null) {
            spectrum = loadingFilter.apply(spectrum);
        }

        IBinarySpectrum s = new BinarySpectrum(
                ((BasicIntegerNormalizer)precursorNormalizer).binValue(spectrum.getPrecursorMZ()),
                (spectrum.getPrecursorCharge() != null) ? spectrum.getPrecursorCharge() : 0,
                factory.normalizePeaks(spectrum.getPeakList()),
                comparisonFilter);

        // save spectrum properties
        if (propertyStorage != null) {
            for (Param param: spectrum.getAdditional()) {
                propertyStorage.put(s.getUUI(), param.getName(), param.getValue());

                // TODO: map the title and retention time from existing cvParams
                // current implementation might only work for MGF files.

                // TODO: put support for PTMs
            }
            // always store the original filename
            propertyStorage.put(s.getUUI(), StoredProperties.ORG_FILENAME, inputFile.getName());

            String spectrumId = spectrum.getId();

            // make spectrum id PSI format compatible
            if (spectrum instanceof Ms2Query) {
                spectrumId = "index=" + spectrumId;
            }

            propertyStorage.put(s.getUUI(), StoredProperties.FILE_INDEX, spectrumId);
            propertyStorage.put(s.getUUI(), StoredProperties.PRECURSOR_MZ, String.valueOf(spectrum.getPrecursorMZ()));
            propertyStorage.put(s.getUUI(), StoredProperties.CHARGE, String.valueOf(spectrum.getPrecursorCharge()));

            // save the original peaklist
            StringBuilder mzValues = new StringBuilder(50);
            StringBuilder intensValues = new StringBuilder(50);

            boolean isFirst = true;

            for (Double mz : top50Peaks.keySet()) {
                // add the delimiter
                if (!isFirst) {
                    mzValues.append(",");
                    intensValues.append(",");
                } else {
                    isFirst = false;
                }

                mzValues.append(String.format("%.4f", mz));
                intensValues.append(String.format("%.4f", top50Peaks.get(mz)));
            }

            propertyStorage.put(s.getUUI(), StoredProperties.ORIGINAL_PEAKS_MZ, mzValues.toString());
            propertyStorage.put(s.getUUI(), StoredProperties.ORIGINAL_PEAKS_INTENS, intensValues.toString());
        }

        // call the listeners
        for (ISpectrumListener spectrumListener : listener) {
            spectrumListener.onNewSpectrum(s);
        }

        return s;
    }


    /**
     * Get the Class for the specific Peak List reader
     * @param file File to be read
     */
    private static Class<?> isValidPeakListFile(File file){

        String filename = file.getName().toLowerCase();

        if (filename.endsWith(MzFileType.MGF.getExtension()))
            return MgfIterableReader.class;
        else if(filename.endsWith(MzFileType.CLUSTERING.getExtension()))
            return ObjectDBGreedyClusterStorage.class;
//        else if (filename.endsWith(MzFileType.MS2.getExtension()))
//            return Ms2File.class;
//        else if (filename.endsWith(MzFileType.PKL.getExtension()))
//            return PklFile.class;
//        else if (filename.endsWith(MzFileType.APL.getExtension()))
//            return AplFile.class;

        return null;
    }

    /**
     * Check if the following file provided is a proper mzML. In this case an extra check is done
     * to see if the file inside contains mzML data.
     *
     * @param file File to be processed
     * @return True if is a valide mzML
     */
    private static boolean isValidMzML(File file){
        return checkXMLValidFile(file, mzMLHeaderPattern, MzFileType.MZML);
    }

    /**
     * Check if the following file provided is a proper mzXML. In this case an extra check is done
     * to see if the file inside contains mzXML data.
     *
     * @param file File to be processed
     * @return True if is a valide mzXML
     */
    private static boolean isValidmzXML(File file){
        return checkXMLValidFile(file, mzXmlHeaderPattern, MzFileType.MZXML);
    }

    /**
     * This function that check if the first lines of an XML file contains an specific
     * Pattern. It also validates that the file extension correspond to the {@link MzFileType}
     * @param file File
     * @param pattern Pattern to be search
     * @param fileType {@link MzFileType}
     * @return True if the extension and the pattern match .
     */
    private static boolean checkXMLValidFile(File file, Pattern pattern, MzFileType fileType){
        boolean valid = false;
        String filename = file.getName().toLowerCase();
        if(filename.endsWith(fileType.getExtension())){
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                StringBuilder content = new StringBuilder();
                for (int i = 0; i < 10; i++) {
                    content.append(reader.readLine());
                }
                Matcher matcher = pattern.matcher(content);
                valid = matcher.find();
            } catch (Exception e) {
                log.error("Failed to read the provided file -- " + file.getAbsolutePath(), e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.error("The File is not an valid -- + " + fileType.getName() +  " -- " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return valid;

    }

    /**
     * Add a new listener that will receive every loaded spectrum.
     *
     * The listener is called **after** any pre-processing of the spectrum
     * was performed.
     *
     * @param newListener The new listener
     */
    public void addSpectrumListener(ISpectrumListener newListener) {
        listener.add(newListener);
    }

    public void removeSpectrumListener(ISpectrumListener theListener) {
        listener.remove(theListener);
    }
}
