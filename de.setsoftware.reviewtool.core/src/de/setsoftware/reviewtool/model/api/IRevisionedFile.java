package de.setsoftware.reviewtool.model.api;

import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

/**
 * Denotes a certain revision of a file.
 */
public interface IRevisionedFile {

    /**
     * Returns the path of the file (relative to the SCM repository root).
     */
    public abstract String getPath();

    /**
     * Returns the revision of the file.
     */
    public abstract IRevision getRevision();

    /**
     * Returns the repository of the file. Concides with {@code getRevision().getRepository()}.
     */
    public abstract IRepository getRepository();

    /**
     * Returns this revisioned file's contents.
     * @return The file contents or null if unknown.
     * @throws IOException if an error occurrs.
     */
    public abstract byte[] getContents() throws Exception;

    /**
     * Finds a resource corresponding to a path that is relative to the SCM repository root.
     * If none can be found, null is returned.
     */
    public abstract IResource determineResource();

    /**
     * Returns the absolute path of the file in the local working copy.
     */
    public abstract IPath toLocalPath();

}
