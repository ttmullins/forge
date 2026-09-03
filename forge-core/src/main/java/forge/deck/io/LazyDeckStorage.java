package forge.deck.io;

import forge.deck.Deck;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;
import forge.util.storage.StorageNestedFolders;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Filesystem-backed deck storage that indexes deck filenames eagerly but
 * deserializes individual {@link Deck} objects only when they are requested.
 *
 * <p>The regular {@link DeckStorage}/{@code StorageImmediatelySerialized}
 * path calls {@code readAll()}, which opens and parses every .dck file when a
 * storage folder is first visited. That is convenient for small collections,
 * but makes large user libraries spend most of their startup time doing I/O
 * and allocating Deck/CardPool objects that may never be used.</p>
 *
 * <p>This storage keeps only a lightweight filename -&gt; File index. The file
 * stem is used as the provisional deck name; Forge-created deck files already
 * use the deck's best filename, so normal user decks preserve their expected
 * names. Once a deck is actually loaded, its real metadata name is also cached
 * as an alias.</p>
 */
public final class LazyDeckStorage implements IStorage<Deck> {
    private static final String DCK_EXTENSION = DeckStorage.FILE_EXTENSION;

    private final String name;
    private final File directory;
    private final String rootDir;
    private final boolean moveWronglyNamedDecks;
    private final DeckStorage serializer;

    /** Primary lightweight catalog. Values are not opened while indexing. */
    private final Map<String, File> filesByName = new ConcurrentHashMap<>();

    /** Decks that have actually been requested during this Forge session. */
    private final Map<String, Deck> loadedDecks = new ConcurrentHashMap<>();

    private volatile IStorage<IStorage<Deck>> subfolders;

    public LazyDeckStorage(final String name0, final File directory0, final String rootDir0) {
        this(name0, directory0, rootDir0, false);
    }

    public LazyDeckStorage(final String name0, final File directory0, final String rootDir0,
            final boolean moveWrongDecks) {
        if (directory0 == null) {
            throw new IllegalArgumentException("No directory specified");
        }

        name = name0;
        directory = directory0;
        rootDir = rootDir0;
        moveWronglyNamedDecks = moveWrongDecks;

        if (directory.isFile()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Directory can't be created: " + directory);
        }
        if (!directory.isDirectory()) {
            throw new IllegalStateException("Not a directory: " + directory);
        }

        serializer = new DeckStorage(directory, rootDir, moveWronglyNamedDecks);
        indexFiles();
    }

    private void indexFiles() {
        final File[] files = directory.listFiles(DeckStorage.DCK_FILE_FILTER);
        if (files == null) {
            return;
        }

        for (final File file : files) {
            filesByName.put(deckNameFromFilename(file.getName()), file);
        }
    }

    private static String deckNameFromFilename(final String filename) {
        if (filename.endsWith(DCK_EXTENSION)) {
            return filename.substring(0, filename.length() - DCK_EXTENSION.length());
        }
        return filename;
    }

    /**
     * Re-scan directory entries without touching deck contents. Useful after
     * external tools copy/remove .dck files while Forge is running.
     */
    public synchronized void refreshIndex() {
        filesByName.clear();
        loadedDecks.clear();
        subfolders = null;
        indexFiles();
    }

    /** Number of full Deck objects currently materialized in memory. */
    public int getLoadedDeckCount() {
        return (int) loadedDecks.values().stream().distinct().count();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFullPath() {
        return directory.getPath();
    }

    @Override
    public Deck get(final String deckName) {
        if (deckName == null) {
            return null;
        }

        final Deck cached = loadedDecks.get(deckName);
        if (cached != null) {
            return cached;
        }

        final File file = filesByName.get(deckName);
        if (file == null) {
            return null;
        }

        final Deck loaded = serializer.read(file);
        if (loaded == null) {
            return null;
        }

        loadedDecks.put(deckName, loaded);
        if (loaded.getName() != null && !loaded.getName().isEmpty()) {
            loadedDecks.putIfAbsent(loaded.getName(), loaded);
        }

        // DeckStorage may move a wrongly named deck when that legacy behavior
        // is enabled. Keep the lightweight index truthful after the move.
        if (moveWronglyNamedDecks && !file.exists()) {
            filesByName.remove(deckName, file);
        }

        return loaded;
    }

    @Override
    public Deck find(final Predicate<Deck> condition) {
        if (condition == null) {
            return null;
        }
        for (final String deckName : getItemNames()) {
            final Deck deck = get(deckName);
            if (deck != null && condition.test(deck)) {
                return deck;
            }
        }
        return null;
    }

    @Override
    public Collection<String> getItemNames() {
        return new ArrayList<>(filesByName.keySet());
    }

    @Override
    public boolean contains(final String deckName) {
        return deckName != null && (filesByName.containsKey(deckName) || loadedDecks.containsKey(deckName));
    }

    @Override
    public int size() {
        return filesByName.size();
    }

    @Override
    public void add(final Deck deck) {
        if (deck == null) {
            return;
        }

        serializer.save(deck);
        final File file = serializer.makeFileFor(deck);
        final String indexedName = deckNameFromFilename(file.getName());
        filesByName.put(indexedName, file);
        loadedDecks.put(indexedName, deck);
        if (deck.getName() != null && !deck.getName().isEmpty()) {
            loadedDecks.put(deck.getName(), deck);
        }
    }

    @Override
    public void add(final String deckName, final Deck deck) {
        add(deck);
    }

    @Override
    public void delete(final String deckName) {
        if (deckName == null) {
            return;
        }

        final File file = filesByName.remove(deckName);
        final Deck loaded = loadedDecks.remove(deckName);

        if (loaded != null) {
            loadedDecks.entrySet().removeIf(entry -> entry.getValue() == loaded);
        }

        if (file != null) {
            // Match the old serializer's best-effort delete semantics.
            file.delete();
        } else if (loaded != null) {
            serializer.erase(loaded);
        }
    }

    @Override
    public Iterator<Deck> iterator() {
        final Iterator<String> names = getItemNames().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return names.hasNext();
            }

            @Override
            public Deck next() {
                final String deckName = names.next();
                final Deck deck = get(deckName);
                if (deck == null) {
                    throw new NoSuchElementException("Deck failed to load: " + deckName);
                }
                return deck;
            }
        };
    }

    @Override
    public Stream<Deck> stream() {
        return getItemNames().stream().map(this::get).filter(Objects::nonNull);
    }

    private Iterable<File> listSubfolders() {
        final File[] folders = directory.listFiles(file -> file.isDirectory() && !file.isHidden());
        return folders == null ? Collections.emptyList() : Arrays.asList(folders);
    }

    @Override
    public IStorage<IStorage<Deck>> getFolders() {
        IStorage<IStorage<Deck>> result = subfolders;
        if (result == null) {
            synchronized (this) {
                result = subfolders;
                if (result == null) {
                    result = new StorageNestedFolders<>(directory, listSubfolders(),
                            file -> new LazyDeckStorage(file.getName(), file, rootDir, false));
                    subfolders = result;
                }
            }
        }
        return result;
    }

    @Override
    public IStorage<Deck> tryGetFolder(final String path) {
        final String normalized = normalizePath(path);
        if (normalized.isEmpty() || ".".equals(normalized)) {
            return this;
        }

        final int slash = normalized.indexOf('/');
        final String head = slash < 0 ? normalized : normalized.substring(0, slash);
        final String tail = slash < 0 ? "" : normalized.substring(slash + 1);
        final IStorage<Deck> child = getFolders().get(head);
        if (child == null) {
            return null;
        }
        return tail.isEmpty() ? child : child.tryGetFolder(tail);
    }

    @Override
    public IStorage<Deck> getFolderOrCreate(final String path) {
        final String normalized = normalizePath(path);
        if (normalized.isEmpty() || ".".equals(normalized)) {
            return this;
        }

        final int slash = normalized.indexOf('/');
        final String head = slash < 0 ? normalized : normalized.substring(0, slash);
        final String tail = slash < 0 ? "" : normalized.substring(slash + 1);

        IStorage<Deck> child = getFolders().get(head);
        if (child == null) {
            final File childDirectory = new File(directory, head);
            if (!childDirectory.exists() && !childDirectory.mkdir()) {
                throw new IllegalStateException("Unable to create deck folder: " + childDirectory);
            }
            child = new LazyDeckStorage(head, childDirectory, rootDir, false);
            subfolders = null; // rebuild folder catalog on next access
        }

        return tail.isEmpty() ? child : child.getFolderOrCreate(tail);
    }

    private static String normalizePath(final String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
