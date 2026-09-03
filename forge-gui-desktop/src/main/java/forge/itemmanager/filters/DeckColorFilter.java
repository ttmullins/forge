package forge.itemmanager.filters;

import javax.swing.JPanel;

import forge.deck.DeckProxy;
import forge.itemmanager.ItemManager;
import forge.itemmanager.SFilterUtil;
import forge.itemmanager.SItemManagerUtil.StatTypes;
import forge.util.ItemPool;

import java.util.function.Predicate;


public class DeckColorFilter extends StatTypeFilter<DeckProxy> {
    public DeckColorFilter(ItemManager<? super DeckProxy> itemManager0) {
        super(itemManager0);
    }

    @Override
    public ItemFilter<DeckProxy> createCopy() {
        return new DeckColorFilter(itemManager);
    }

    @Override
    protected void buildWidget(JPanel widget) {
        addToggleButton(widget, StatTypes.DECK_WHITE);
        addToggleButton(widget, StatTypes.DECK_BLUE);
        addToggleButton(widget, StatTypes.DECK_BLACK);
        addToggleButton(widget, StatTypes.DECK_RED);
        addToggleButton(widget, StatTypes.DECK_GREEN);
        addToggleButton(widget, StatTypes.DECK_COLORLESS);
        addToggleButton(widget, StatTypes.DECK_MULTICOLOR);
    }

    private boolean isShowingAllColors() {
        return buttonMap.values().stream().allMatch(button -> button.isSelected());
    }

    @Override
    protected final Predicate<DeckProxy> buildPredicate() {
        // The default state accepts every color. Avoid asking each DeckProxy for
        // its color, since doing so materializes LazyFileDeck instances and turns
        // simply opening a large deck library back into an eager full-file scan.
        if (isShowingAllColors()) {
            return deck -> true;
        }
        return SFilterUtil.buildDeckColorFilter(buttonMap);
    }

    @Override
    public void afterFiltersApplied() {
        // Exact color counts require inspecting deck contents. Do not defeat lazy
        // deck loading just to populate the default filter badge counts. Once the
        // user actively filters by color, the relevant decks have to be inspected
        // anyway and the counts can be calculated normally.
        if (isShowingAllColors()) {
            return;
        }

        final ItemPool<? super DeckProxy> items = itemManager.getFilteredItems();

        buttonMap.get(StatTypes.DECK_WHITE).setText(String.valueOf(items.countAll(DeckProxy.IS_WHITE, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_BLUE).setText(String.valueOf(items.countAll(DeckProxy.IS_BLUE, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_BLACK).setText(String.valueOf(items.countAll(DeckProxy.IS_BLACK, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_RED).setText(String.valueOf(items.countAll(DeckProxy.IS_RED, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_GREEN).setText(String.valueOf(items.countAll(DeckProxy.IS_GREEN, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_COLORLESS).setText(String.valueOf(items.countAll(DeckProxy.IS_COLORLESS, DeckProxy.class)));
        buttonMap.get(StatTypes.DECK_MULTICOLOR).setText(String.valueOf(items.countAll(DeckProxy.IS_MULTICOLOR, DeckProxy.class)));

        getWidget().revalidate();
    }
}
