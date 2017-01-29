package de.setsoftware.reviewtool.changesources.svn;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

import de.setsoftware.reviewtool.base.Logger;

/**
 * A local cache of the SVN log(s) to speed up the gathering of relevant entries.
 */
public class CachedLog {

    /**
     * Data regarding the repository. Is only cached in memory.
     */
    private static final class RepoDataCache {

        private final String relPath;
        private final SvnRepo repo;

        public RepoDataCache(String relPath, SvnRepo repo) {
            this.relPath = relPath;
            this.repo = repo;
        }

        public SvnRepo getRepo() {
            return this.repo;
        }
    }

    private static final int LOOKUP_LIMIT = 1000;
    private static final CachedLog INSTANCE = new CachedLog();

    private final Map<String, RepoDataCache> repoDataPerWcRoot;
    private final Map<String, List<CachedLogEntry>> entriesPerWcRoot;

    private CachedLog() {
        this.repoDataPerWcRoot = new HashMap<>();
        this.entriesPerWcRoot = new HashMap<>();

        try {
            this.readCacheFromFile();
        } catch (ClassNotFoundException | IOException e) {
            Logger.error("problem while loading svn cache", e);
        }
    }

    public static CachedLog getInstance() {
        return INSTANCE;
    }

    /**
     * Calls the given handler for all recent log entries of the given working copy root.
     */
    public void traverseRecentEntries(
            SVNClientManager mgr, File workingCopyRoot, final CachedLogLookupHandler handler) throws SVNException {

        final RepoDataCache repoCache = this.getRepoCache(mgr, workingCopyRoot);
        handler.startNewRepo(repoCache.getRepo());
        for (final CachedLogEntry entry : this.getEntries(mgr, repoCache)) {
            handler.handleLogEntry(entry);
        }
    }

    private synchronized RepoDataCache getRepoCache(SVNClientManager mgr, File workingCopyRoot) throws SVNException {
        RepoDataCache c = this.repoDataPerWcRoot.get(workingCopyRoot.toString());
        if (c == null) {
            final SVNURL rootUrl = mgr.getLogClient().getReposRoot(workingCopyRoot, null, SVNRevision.HEAD);
            final SVNURL wcUrl = mgr.getWCClient().doInfo(workingCopyRoot, SVNRevision.WORKING).getURL();
            final String relPath = wcUrl.toString().substring(rootUrl.toString().length());
            c = new RepoDataCache(relPath, new SvnRepo(
                    mgr,
                    workingCopyRoot,
                    rootUrl,
                    this.determineCheckoutPrefix(mgr, workingCopyRoot, rootUrl)));
            this.repoDataPerWcRoot.put(workingCopyRoot.toString(), c);
        }
        return c;
    }

    private synchronized List<CachedLogEntry> getEntries(SVNClientManager mgr, RepoDataCache repoCache)
        throws SVNException {

        final String wcRootString = repoCache.getRepo().getLocalRoot().toString();
        List<CachedLogEntry> list = this.entriesPerWcRoot.get(wcRootString);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            this.entriesPerWcRoot.put(wcRootString, list);
        }

        final boolean gotNewEntries = this.loadNewEntries(mgr, repoCache, list);

        if (gotNewEntries) {
            try {
                this.storeCacheToFile();
            } catch (final IOException e) {
                Logger.error("problem while caching svn log", e);
            }
        }

        return list;
    }

    private boolean loadNewEntries(SVNClientManager mgr, RepoDataCache repoCache, List<CachedLogEntry> list)
        throws SVNException {

        final long lastKnownRevision = list.isEmpty() ? 0 : list.get(0).getRevision();

        final ArrayList<CachedLogEntry> newEntries = new ArrayList<>();
        mgr.getLogClient().doLog(
                repoCache.getRepo().getRemoteUrl(),
                new String[] { repoCache.relPath },
                SVNRevision.HEAD,
                SVNRevision.HEAD,
                SVNRevision.create(lastKnownRevision),
                false,
                true,
                false,
                LOOKUP_LIMIT,
                new String[0],
                new ISVNLogEntryHandler() {
                    @Override
                    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                        if (logEntry.getRevision() > lastKnownRevision) {
                            newEntries.add(new CachedLogEntry(logEntry));
                        }
                    }
                });

        Collections.sort(newEntries, new Comparator<CachedLogEntry>() {
            @Override
            public int compare(CachedLogEntry o1, CachedLogEntry o2) {
                return Long.compare(o2.getRevision(), o1.getRevision());
            }
        });
        list.addAll(0, newEntries);
        return !newEntries.isEmpty();
    }

    private int determineCheckoutPrefix(SVNClientManager mgr, File workingCopyRoot, SVNURL rootUrl)
        throws SVNException {

        SVNURL checkoutRootUrlPrefix = mgr.getWCClient().doInfo(workingCopyRoot, SVNRevision.HEAD).getURL();
        int i = 0;
        while (!(checkoutRootUrlPrefix.equals(rootUrl) || checkoutRootUrlPrefix.getPath().equals("//"))) {
            checkoutRootUrlPrefix = checkoutRootUrlPrefix.removePathTail();
            i++;
        }
        return i;
    }

    private void readCacheFromFile() throws IOException, ClassNotFoundException {
        final File file = this.getCacheFilePath().toFile();
        if (!file.exists()) {
            return;
        }
        try (ObjectInputStream ois =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            while (true) {
                String key;
                try {
                    key = ois.readUTF();
                } catch (final EOFException ex) {
                    break;
                }
                final List<CachedLogEntry> value = (List<CachedLogEntry>) ois.readObject();
                this.entriesPerWcRoot.put(key, value);
            }
        }
    }

    private void storeCacheToFile() throws IOException {
        final IPath file = this.getCacheFilePath();
        try (ObjectOutputStream oos =
                new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            int entryCount = 0;
            for (final Entry<String, List<CachedLogEntry>> e : this.entriesPerWcRoot.entrySet()) {
                oos.writeUTF(e.getKey());
                oos.writeObject(e.getValue());
                entryCount++;
                if (entryCount > LOOKUP_LIMIT) {
                    break;
                }
            }
        }
    }

    private IPath getCacheFilePath() {
        final Bundle bundle = FrameworkUtil.getBundle(this.getClass());
        final IPath dir = Platform.getStateLocation(bundle);
        return dir.append("svnlog.cache");
    }

}