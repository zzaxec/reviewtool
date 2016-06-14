package de.setsoftware.reviewtool.model.changestructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import de.setsoftware.reviewtool.base.Multimap;

/**
 * A tour through a part of the changes. The consists of stops in a certain order, with
 * each stop belonging to some part of the change.
 * <p/>
 * The Tour+Stop metaphor is borrowed from the JTourBus tool.
 */
public class Tour {

    private final String description;
    private final List<Stop> stops = new ArrayList<>();

    public Tour(String description, List<? extends Stop> list) {
        this.description = description;
        this.stops.addAll(list);
    }

    @Override
    public String toString() {
        return "Tour: " + this.description + ", " + this.stops;
    }

    public List<Stop> getStops() {
        return this.stops;
    }

    public String getDescription() {
        return this.description;
    }

    /**
     * Merges this tour with the given tour.
     * The description of the result is the concatenation of both descriptions, joined with " + ".
     * The stops are merged recursively and sorted by file and position in file. When in doubt, files
     * from this come before files from the other tour.
     */
    public Tour mergeWith(Tour t2) {
        final Multimap<FileInRevision, Stop> stopsInFile = new Multimap<>();

        for (final Stop s : this.stops) {
            stopsInFile.put(s.getMostRecentFile(), s);
        }
        for (final Stop s : t2.stops) {
            stopsInFile.put(s.getMostRecentFile(), s);
        }

        final List<Stop> mergedStops = new ArrayList<>();
        for (final Entry<FileInRevision, List<Stop>> e : stopsInFile.entrySet()) {
            mergedStops.addAll(this.sortByLine(this.mergeInSameFile(e.getValue())));
        }
        return new Tour(this.getDescription() + " + " + t2.getDescription(), mergedStops);
    }

    private List<Stop> mergeInSameFile(List<Stop> stopsInSameFile) {
        final List<Stop> ret = new ArrayList<>();
        final List<Stop> remainingStops = new LinkedList<>(stopsInSameFile);
        while (!remainingStops.isEmpty()) {
            Stop curMerged = remainingStops.remove(0);
            final Iterator<Stop> iter = remainingStops.iterator();
            while (iter.hasNext()) {
                final Stop s = iter.next();
                if (curMerged.canBeMergedWith(s)) {
                    iter.remove();
                    curMerged = curMerged.merge(s);
                }
            }
            ret.add(curMerged);
        }
        return ret;
    }

    private List<Stop> sortByLine(List<Stop> stopsInSameFile) {
        Collections.sort(stopsInSameFile, new Comparator<Stop>() {
            @Override
            public int compare(Stop o1, Stop o2) {
                return Integer.compare(this.getLine(o1), this.getLine(o2));
            }

            private int getLine(Stop s) {
                if (s.getMostRecentFragment() == null) {
                    return 0;
                } else {
                    return s.getMostRecentFragment().getFrom().getLine();
                }
            }
        });
        return stopsInSameFile;
    }

}
