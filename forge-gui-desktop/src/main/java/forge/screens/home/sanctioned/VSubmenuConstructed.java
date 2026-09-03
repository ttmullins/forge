package forge.screens.home.sanctioned;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import forge.deckchooser.FDeckChooser;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LocalLobby;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.screens.home.VHomeUI;
import forge.screens.home.VLobby;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * Assembles Swing components of constructed submenu singleton.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 *
 */
public enum VSubmenuConstructed implements IVSubmenu<CSubmenuConstructed> {

    SINGLETON_INSTANCE;
    final Localizer localizer = Localizer.getInstance();
    // Fields used with interface IVDoc
    private DragCell parentCell;
    private final DragTab tab = new DragTab(localizer.getMessage("lblConstructedMode"));
    private final GameLobby lobby = new LocalLobby();
    private final VLobby vLobby = new VLobby(lobby);
    private boolean lobbyInitialized;

    VSubmenuConstructed() {
        lobby.setListener(vLobby);

        vLobby.setPlayerChangeListener(lobby::applyToSlot);

        // Do not initialize deck choosers here. This singleton is constructed while
        // FView is still opening the main window, and VLobby.update(false) creates
        // and populates FDeckChooser instances. With very large constructed deck
        // libraries that can block the Swing EDT before the main frame is visible.
    }

    public VLobby getLobby() {
        return vLobby;
    }

    /////////////////////////////////////
    //========== Overridden from IVDoc

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getDocumentID()
     */
    @Override
    public EDocID getDocumentID() {
        return EDocID.HOME_CONSTRUCTED;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getTabLabel()
     */
    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getLayoutControl()
     */
    @Override
    public CSubmenuConstructed getLayoutControl() {
        return CSubmenuConstructed.SINGLETON_INSTANCE;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#setParentCell(forge.gui.framework.DragCell)
     */
    @Override
    public void setParentCell(DragCell cell0) {
        this.parentCell = cell0;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getParentCell()
     */
    @Override
    public DragCell getParentCell() {
        return parentCell;
    }

    /* (non-Javadoc)
     * @see forge.gui.home.IVSubmenu#getGroupEnum()
     */
    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.SANCTIONED;
    }

    /* (non-Javadoc)
     * @see forge.gui.home.IVSubmenu#getMenuTitle()
     */
    @Override
    public String getMenuTitle() {

        return localizer.getMessage("lblConstructed");
    }

    /* (non-Javadoc)
     * @see forge.gui.home.IVSubmenu#getItemEnum()
     */
    @Override
    public EDocID getItemEnum() {
        return EDocID.HOME_CONSTRUCTED;
    }

    /* (non-Javadoc)
     * @see forge.gui.home.IVSubmenu#populate()
     */
    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();

        container.removeAll();
        container.setLayout(new MigLayout("insets 0, gap 0, wrap 1, ax right"));
        container.add(vLobby.getLblTitle(), "w 80%, h 40px!, gap 0 0 15px 15px, span 2, al right, pushx");
        container.add(vLobby.getConstructedFrame(), "gap 20px 20px 20px 0px, push, grow");
        container.add(vLobby.getPanelStart(), "gap 0 0 3.5%! 3.5%!, ax center");

        if (!lobbyInitialized) {
            lobbyInitialized = true;

            // Let the current main-window initialization event finish first. The
            // deferred lobby update may still have substantial deck-index work to
            // do, but it no longer prevents Forge from reaching and displaying the
            // main frame behind the startup splash.
            SwingUtilities.invokeLater(() -> {
                vLobby.update(false);
                refreshContainer(container);
            });
        } else {
            for (final FDeckChooser fdc : vLobby.getDeckChoosers()) {
                fdc.populate();
            }
            refreshContainer(container);
        }
    }

    private void refreshContainer(final JPanel container) {
        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }

        if (!vLobby.getPlayerPanels().isEmpty()) {
            vLobby.changePlayerFocus(0);
        }
    }
}
