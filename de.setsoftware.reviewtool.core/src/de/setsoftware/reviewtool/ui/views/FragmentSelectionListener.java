package de.setsoftware.reviewtool.ui.views;

import de.setsoftware.reviewtool.model.changestructure.SliceFragment;

/**
 * Interface for listeners that are called when the currently selected
 * slice fragment changes.
 */
public interface FragmentSelectionListener {

    /**
     * Is called when a new fragment is selected or when a fragment is unselected.
     * In case of unselection, the given fragment is null.
     */
    public abstract void notifyFragmentChange(SliceFragment fragment);

}
