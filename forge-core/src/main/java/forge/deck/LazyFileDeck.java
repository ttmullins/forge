package forge.deck;

import forge.item.PaperCard;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lightweight deck placeholder backed by a supplier for the real deck file.
 *
 * <p>The object exposes its filename-derived name immediately, which is enough
 * for Forge's default deck list/folder/name handling. Operations that actually
 * need deck metadata or card contents materialize the real deck exactly once
 * and copy its state into this object.</p>
 */
public final class LazyFileDeck extends Deck {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient Supplier<Deck> loader;
    private transient boolean loaded;
    private transient boolean loaderReady;

    public LazyFileDeck(final String provisionalName, final Supplier<Deck> loader0) {
        // Deck's constructor creates the Main section by calling virtual get().
        // Keep lazy dispatch inert until super(...) has completed.
        super(provisionalName);
        loader = loader0;
        loaderReady = true;
    }

    public boolean isMaterialized() {
        return loaded;
    }

    private synchronized void ensureLoaded() {
        if (loaded || !loaderReady) {
            return;
        }
        if (loader == null) {
            throw new IllegalStateException("Lazy deck has no backing loader: " + getName());
        }

        final Deck actual = loader.get();
        if (actual == null) {
            throw new IllegalStateException("Unable to load deck: " + getName());
        }

        // Deck.cloneFieldsTo performs the existing deferred-section load and
        // copies every deck-specific field into this instance.
        actual.cloneFieldsTo(this);
        setName(actual.getName());
        loaded = true;
        loader = null;
    }

    @Override
    public CardPool getMain() {
        ensureLoaded();
        return super.getMain();
    }

    @Override
    public CardPool get(final DeckSection deckSection) {
        ensureLoaded();
        return super.get(deckSection);
    }

    @Override
    public Iterator<Map.Entry<DeckSection, CardPool>> iterator() {
        ensureLoaded();
        return super.iterator();
    }

    @Override
    public Set<String> getTags() {
        ensureLoaded();
        return super.getTags();
    }

    @Override
    public CardPool getAllCardsInASinglePool() {
        ensureLoaded();
        return super.getAllCardsInASinglePool();
    }

    @Override
    public CardPool getAllCardsInASinglePool(final boolean includeCommander, final boolean includeExtras) {
        ensureLoaded();
        return super.getAllCardsInASinglePool(includeCommander, includeExtras);
    }

    @Override
    public List<String> getKeyCards() {
        ensureLoaded();
        return super.getKeyCards();
    }

    @Override
    public Map<String, String> getDraftNotes() {
        ensureLoaded();
        return super.getDraftNotes();
    }

    @Override
    public DeckFormat getDeckFormat() {
        ensureLoaded();
        return super.getDeckFormat();
    }

    @Override
    public String getSourceUrl() {
        ensureLoaded();
        return super.getSourceUrl();
    }

    @Override
    public Set<String> getAiHints() {
        ensureLoaded();
        return super.getAiHints();
    }

    @Override
    public String getAiHint(final String name) {
        ensureLoaded();
        return super.getAiHint(name);
    }

    @Override
    public UnplayableAICards getUnplayableAICards() {
        ensureLoaded();
        return super.getUnplayableAICards();
    }

    @Override
    public String getSleeveArtKey() {
        ensureLoaded();
        return super.getSleeveArtKey();
    }

    @Override
    public int getSleeveArtOffset() {
        ensureLoaded();
        return super.getSleeveArtOffset();
    }

    @Override
    public int getAverageCMC() {
        ensureLoaded();
        return super.getAverageCMC();
    }

    @Override
    public int countByName(final String cardName) {
        ensureLoaded();
        return super.countByName(cardName);
    }

    @Override
    public int count(final PaperCard card) {
        ensureLoaded();
        return super.count(card);
    }

    @Override
    public boolean isEmpty() {
        ensureLoaded();
        return super.isEmpty();
    }

    @Override
    public Deck getHumanDeck() {
        ensureLoaded();
        return this;
    }

    @Override
    public String generateTextExport() {
        ensureLoaded();
        return super.generateTextExport();
    }

    @Override
    public DeckBase copyTo(final String name0) {
        ensureLoaded();
        return super.copyTo(name0);
    }

    @Override
    public String getComment() {
        ensureLoaded();
        return super.getComment();
    }

    @Serial
    private Object writeReplace() throws ObjectStreamException {
        ensureLoaded();
        return new Deck(this);
    }
}
