package forge.deck.io;

import forge.deck.Deck;
import forge.deck.LazyFileDeck;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/** Regression tests for large-library lazy deck storage. */
public class LazyDeckStorageTest {

    @Test
    public void enumerationDoesNotOpenDeckFiles() throws IOException {
        final Path root = Files.createTempDirectory("forge-lazy-decks-");
        try {
            writeDeck(root.resolve("Alpha.dck"), "Alpha", "first");
            writeDeck(root.resolve("Beta.dck"), "Beta", "second");

            final LazyDeckStorage storage = new LazyDeckStorage(
                    "Test decks", root.toFile(), root.toString());

            assertEquals(storage.size(), 2);
            assertEquals(storage.getLoadedDeckCount(), 0,
                    "Index construction must not deserialize deck files");

            final Iterator<Deck> decks = storage.iterator();
            assertTrue(decks.hasNext());
            final Deck placeholder = decks.next();
            assertTrue(placeholder instanceof LazyFileDeck);
            assertFalse(((LazyFileDeck) placeholder).isMaterialized());

            // Filename-derived name is available without touching file contents.
            assertNotNull(placeholder.getName());
            assertEquals(storage.getLoadedDeckCount(), 0,
                    "Reading the list/name must stay metadata-only");

            // A content/metadata getter that is not available from the filename
            // should materialize exactly the selected deck.
            assertNotNull(placeholder.getComment());
            assertTrue(((LazyFileDeck) placeholder).isMaterialized());
            assertEquals(storage.getLoadedDeckCount(), 1,
                    "Only the requested deck should have been deserialized");
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void subfoldersAreIndexedWithoutLoadingTheirDecks() throws IOException {
        final Path root = Files.createTempDirectory("forge-lazy-folders-");
        try {
            final Path modern = Files.createDirectories(root.resolve("Modern"));
            writeDeck(modern.resolve("Example.dck"), "Example", "modern");

            final LazyDeckStorage storage = new LazyDeckStorage(
                    "Test decks", root.toFile(), root.toString());
            final forge.util.storage.IStorage<Deck> folder = storage.getFolders().get("Modern");

            assertNotNull(folder);
            assertEquals(folder.size(), 1);
            assertTrue(folder instanceof LazyDeckStorage);
            assertEquals(((LazyDeckStorage) folder).getLoadedDeckCount(), 0);

            final Deck listed = folder.iterator().next();
            assertEquals(listed.getName(), "Example");
            assertEquals(((LazyDeckStorage) folder).getLoadedDeckCount(), 0,
                    "Enumerating a subfolder must not open its decks");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void writeDeck(final Path file, final String name, final String comment) throws IOException {
        final String contents = "[metadata]\n"
                + "Name=" + name + "\n"
                + "Comment=" + comment + "\n"
                + "[main]\n";
        Files.writeString(file, contents, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(final Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }
}
