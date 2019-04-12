package org.spectra.cluster.model.cluster;

import lombok.extern.slf4j.Slf4j;
import org.spectra.cluster.model.consensus.IConsensusSpectrumBuilder;
import org.spectra.cluster.model.spectra.IBinarySpectrum;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * The {@link GreedySpectralCluster} implements the clustering process and creates a {@link org.spectra.cluster.model.consensus.GreedyConsensusSpectrum}. This implementation if ICluster only supports the addition of
 * spectra. It does not keep the actual spectra after they were added to the {@link org.spectra.cluster.model.consensus.GreedyConsensusSpectrum} but only their ids.
 *
 * @author Johannes Griss
 * @author ypriverol
 *
 */
@Slf4j
public class GreedySpectralCluster implements ICluster {
    /**
     * The N (defined here) highest comparison matches will be
     * saved
     */
    public static final int SAVED_COMPARISON_MATCHES = 30;
    private List<ComparisonMatch> bestComparisonMatches = new ArrayList<>(SAVED_COMPARISON_MATCHES);
    private float lowestBestComparisonSimilarity = 0;
    private Set<String> bestComparisonMatchIds = null;

    private String id;

    /**
     * Clustered spectra are only stored as their ids
     */
    private Set<String> clusteredSpectraIds = new HashSet<>();

    private final IConsensusSpectrumBuilder consensusSpectrumBuilder;

    public GreedySpectralCluster(IConsensusSpectrumBuilder consensusSpectrumBuilder) {
        this.id = consensusSpectrumBuilder.getUUI();
        this.consensusSpectrumBuilder = consensusSpectrumBuilder;
    }

    /**
     * return a set of all ids
     *
     * @return A set of strings representing all Ids.
     */
    @Override
    public Set<String> getClusteredSpectraIds() {
        return Collections.unmodifiableSet(clusteredSpectraIds);
    }
    /**
     * if possible use the highest
     *
     * @return The cluster's id
     */
    @Override
    public String getId() {
        return id;
    }

    @Override
    public IConsensusSpectrumBuilder getConsensusSpectrumBuilder() {
        return consensusSpectrumBuilder;
    }

    @Override
    public int getPrecursorMz() {
        if (clusteredSpectraIds.isEmpty()) {
            return -1;
        }

        return consensusSpectrumBuilder.getPrecursorMz();
    }

    @Override
    public int getPrecursorCharge() {
        if (clusteredSpectraIds.isEmpty()) {
            return -1;
        }

        return consensusSpectrumBuilder.getPrecursorCharge();
    }

    @Override
    public IBinarySpectrum getConsensusSpectrum() {
        return consensusSpectrumBuilder.getConsensusSpectrum();
    }

    @Override
    public int getClusteredSpectraCount() {
        return clusteredSpectraIds.size();
    }

    @Override
    public void addSpectra(IBinarySpectrum... spectraToAdd) {
        if (spectraToAdd == null) {
            return;
        }
        if (spectraToAdd.length < 1) {
            return;
        }

        // make sure no duplicate spectra exist
        Set<String> duplicateIds;

        duplicateIds = Arrays.stream(spectraToAdd).filter(x -> clusteredSpectraIds.contains(x.getUUI())).map(IBinarySpectrum::getUUI)
                .collect(Collectors.toSet());

        // this should generally not happen
        if (!duplicateIds.isEmpty()) {
            // stop of all spectra are duplicates
            if (duplicateIds.size() == spectraToAdd.length) {
                return;
            }

            IBinarySpectrum[] filteredSpectra = new IBinarySpectrum[spectraToAdd.length - duplicateIds.size()];
            int addedSpectra = 0;

            for (IBinarySpectrum spectrum : spectraToAdd) {
                if (!duplicateIds.contains(spectrum.getUUI())) {
                    filteredSpectra[addedSpectra++] = spectrum;
                }
            }

            spectraToAdd = filteredSpectra;
        }

        // only add the spectra to the consensus spectrum
        consensusSpectrumBuilder.addSpectra(spectraToAdd);
        // add all spectrum ids
        clusteredSpectraIds.addAll(Arrays.stream(spectraToAdd)
                .map(IBinarySpectrum::getUUI)
                .collect(Collectors.toSet()));
    }

    /**
     * This function enables the merging of clusters that do not save peak lists
     *
     * @param cluster An ICluster to add to the cluster
     */
    @Override
    public void mergeCluster(ICluster cluster) {
        // test if the cluster contains duplicate spectra
        for (String id : cluster.getClusteredSpectraIds()) {
            if (clusteredSpectraIds.contains(id)) {
                log.warn(String.format("Adding duplicate spectra to from cluster %s to cluster %s.",
                        cluster.getId(), this.id));
                break;
            }
        }

        // merge the consensus spectrum
        consensusSpectrumBuilder.addConsensusSpectrum(cluster.getConsensusSpectrumBuilder());

        // adapt the id
        if (cluster.getClusteredSpectraCount() > clusteredSpectraIds.size()) {
            id = cluster.getId();
        }

        // add the clustered spectra
        clusteredSpectraIds.addAll(cluster.getClusteredSpectraIds());

        // add the comparison matches
        if (cluster.getComparisonMatches().size() > 0) {
            for (ComparisonMatch match : cluster.getComparisonMatches()) {
                // make sure not to add any self-references
                if (!match.getSpectrumId().equals(id)) {
                    bestComparisonMatches.add(match);
                }
            }
            updateComparisonMatches();
        }
    }

    /**
     * Saves the comparison match in the best matches array
     *
     * @param id Id of the cluster that the comparison was performed with
     * @param similarity The similarity score to store for this comparison
     */
    public void saveComparisonResult(String id, float similarity) {
        if (bestComparisonMatches.size() >= SAVED_COMPARISON_MATCHES && similarity < lowestBestComparisonSimilarity)
            return;

        ComparisonMatch comparisonMatch = new ComparisonMatch(id, similarity);
        bestComparisonMatches.add(comparisonMatch);
        updateComparisonMatches();
    }

    /**
     * Updates the comparison matches after a match was stored. Removes the lowest scoring
     * items in case there are too many.
     */
    private void updateComparisonMatches() {
        Collections.sort(bestComparisonMatches);

        // remove lowest items in case there are too many
        if (bestComparisonMatches.size() > SAVED_COMPARISON_MATCHES) {
            bestComparisonMatches = new ArrayList<>(bestComparisonMatches.subList(bestComparisonMatches.size() - SAVED_COMPARISON_MATCHES, bestComparisonMatches.size()));
        }

        lowestBestComparisonSimilarity = bestComparisonMatches.get(0).getSimilarity();

        // mark the ids as dirty
        bestComparisonMatchIds = null;
    }

    @Override
    public List<ComparisonMatch> getComparisonMatches() {
        return Collections.unmodifiableList(bestComparisonMatches);
    }

    // This function is only kept in case we need it later on. Currently, I believe that it should be removed.
    @Deprecated
    public void setComparisonMatches(List<ComparisonMatch> comparisonMatches) {
        this.bestComparisonMatches.clear();
        if (comparisonMatches != null && comparisonMatches.size() > 0) {
            this.bestComparisonMatches.addAll(comparisonMatches);

            Collections.sort(bestComparisonMatches);
            lowestBestComparisonSimilarity = bestComparisonMatches.get(0).getSimilarity();
        } else {
            lowestBestComparisonSimilarity = 0;
        }

        bestComparisonMatchIds = null; // delete to mark as dirty
    }

    @Override
    public boolean isKnownComparisonMatch(String clusterId) {
        if (bestComparisonMatchIds == null) {
            bestComparisonMatchIds = bestComparisonMatches.stream().map(ComparisonMatch::getSpectrumId).collect(Collectors.toSet());
        }

        return bestComparisonMatchIds.contains(clusterId);
    }

    @Override
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream streamOut = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(streamOut);
        outputStream.writeObject(this);
        return streamOut.toByteArray();
    }


    public static ICluster fromBytes(byte[] clusterBytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(clusterBytes);
        ObjectInputStream inputStream = new ObjectInputStream(in);
        return ((GreedySpectralCluster) inputStream.readObject());
    }
}