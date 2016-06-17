package org.openstreetmap.josm.plugins.turnlanestagging;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import org.openstreetmap.josm.tools.Shortcut;

/**
 *
 * @author ruben
 */
public class LaunchAction extends JosmAction implements SelectionChangedListener {

    private boolean isselection = false;

    public LaunchAction() {
        super(tr("Turn lanes tagging - editor"),
                "turnlanes-tagging",
                tr("Turn lanes tagging - Editor"),
                Shortcut.registerShortcut("edit:turnlanestaggingeditor",
                        tr("Tool: {0}", tr("turn lanes tagging - editor")),
                        KeyEvent.VK_2, Shortcut.ALT_SHIFT),
                true);
        DataSet.addSelectionListener(this);
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        isselection = true;
        launchEditor();
    }

    @Override
    public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
        setEnabled(newSelection != null && newSelection.size() == 1 && isRoad());
        if (isselection && TagEditorDialog.getInstance().isVisible()) {
            launchEditor();
        }
    }

    protected void launchEditor() {
        if (!isEnabled()) {
            return;
        }
        TagEditorDialog dialog = TagEditorDialog.getInstance();
        dialog.startEditSession();
        dialog.setVisible(true);
    }

    public boolean isRoad() {
        Collection<OsmPrimitive> selection = Main.main.getCurrentDataSet().getSelected();
        for (OsmPrimitive element : selection) {
            for (String key : element.keySet()) {
                if (key.equals("highway")) {
                    return true;
                }
            }
        }
        return false;
    }
}
