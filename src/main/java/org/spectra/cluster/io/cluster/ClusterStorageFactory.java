package org.spectra.cluster.io.cluster;

import io.github.bigbio.pgatk.io.mapcache.IMapStorage;
import org.spectra.cluster.exceptions.SpectraClusterException;
import org.spectra.cluster.io.cluster.old_writer.BinaryClusterStorage;
import org.spectra.cluster.model.cluster.ICluster;

import java.io.File;
import java.io.IOException;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 *
 * This class provide a Factory to retrieve the different flavours of {@link BinaryClusterStorage}.
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *
 *
 * @author ypriverol on 17/10/2018.
 */
public class ClusterStorageFactory {
    /**
     * Create a Static Storage for the clusters. The Static Storage is really fast but it demands
     * pre-allocation of the number of entries that will be stored.
     *
     * The database is deleted on close.
     *
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> buildTemporaryStaticStorage(File dbDirectory, long numberEntries) throws SpectraClusterException {
        try {
            return new ChronicleMapClusterStorage(dbDirectory, numberEntries, true);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the ChronicleMap Cluster storage -- " + e.getMessage());
        }
    }

    /**
     * Create a Static Storage for the clusters. The Static Storage is really fast but it demands
     * pre-allocation of the number of entries that will be stored.
     *
     * The database is not deleted when closed.
     *
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> buildPersistentStaticStorage(File dbDirectory, long numberEntries) throws SpectraClusterException {
        try {
            return new ChronicleMapClusterStorage(dbDirectory, numberEntries, false);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the ChronicleMap Cluster storage -- " + e.getMessage());
        }
    }

    /**
     * Open an existing Static Storage for the clusters. The Static Storage is really fast but it demands
     * pre-allocation of the number of entries that will be stored.
     *
     * The database is not deleted when closed.
     *
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> openPersistentStaticStorage(File dbDirectory) throws SpectraClusterException {
        try {
            return new ChronicleMapClusterStorage(dbDirectory, true);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the ChronicleMap Cluster storage -- " + e.getMessage());
        }
    }

    /**
     * Create a Dynamic Storage for the clusters. Depending on errors in the
     * file system. This can return a null value.
     *
     * This function fails if the set directory is not empty.
     *
     * @param  dbDirectory file Path for the file
     * @param clusterClass The cluster Class Implementation that will be storage (e.g. {@link org.spectra.cluster.model.cluster.GreedySpectralCluster})
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> buildDynamicStorage(File dbDirectory, Class clusterClass) throws SpectraClusterException {
        try {
            return new SparkKeyClusterStorage(dbDirectory, clusterClass, false, false);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the SparkKey Cluster storage -- " + e.getMessage());
        }
    }

    /**
     * Create a Dynamic Storage for the clusters that is deleted on close. Depending on errors in the
     * file system. This can return a null value.
     *
     * This function fails if the set directory is not empty.
     *
     * @param  dbDirectory file Path for the file
     * @param clusterClass The cluster Class Implementation that will be storage (e.g. {@link org.spectra.cluster.model.cluster.GreedySpectralCluster})
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> buildTemporaryDynamicStorage(File dbDirectory, Class clusterClass) throws SpectraClusterException {
        try {
            return new SparkKeyClusterStorage(dbDirectory, clusterClass, false, true);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the SparkKey Cluster storage -- " + e.getMessage());
        }
    }

    /**
     * Create a Dynamic Storage for the clusters. Depending on erros in the
     * file system. This can return a null value.
     *
     * @param  dbDirectory file Path for the file
     * @param clusterClass The cluster Class Implementation that will be storage (e.g. {@link org.spectra.cluster.model.cluster.GreedySpectralCluster})
     * @return BinaryClusterStorage
     */
    public static IMapStorage<ICluster> openDynamicStorage(File dbDirectory, Class clusterClass) throws SpectraClusterException {
        try {
            return new SparkKeyClusterStorage(dbDirectory, clusterClass, true, false);
        } catch (IOException e) {
            throw new SpectraClusterException("Error creating the SparkKey Cluster storage -- " + e.getMessage());
        }
    }


}
