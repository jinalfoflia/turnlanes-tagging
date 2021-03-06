package org.openstreetmap.josm.plugins.turnlanestagging;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.AbstractAction;
import static javax.swing.Action.ACCELERATOR_KEY;
import static javax.swing.Action.NAME;
import static javax.swing.Action.SHORT_DESCRIPTION;
import static javax.swing.Action.SMALL_ICON;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.tagging.TagEditorModel;
import org.openstreetmap.josm.plugins.turnlanestagging.bean.BRoad;
import org.openstreetmap.josm.plugins.turnlanestagging.editor.TagEditor;
import org.openstreetmap.josm.plugins.turnlanestagging.editor.ac.KeyValuePair;
import org.openstreetmap.josm.plugins.turnlanestagging.preset.PresetsTableModel;
import org.openstreetmap.josm.plugins.turnlanestagging.buildturnlanes.BuildTurnLanes;
import org.openstreetmap.josm.plugins.turnlanestagging.preset.PresetsData;
import org.openstreetmap.josm.plugins.turnlanestagging.util.Util;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.gui.ExtendedDialog;
import static org.openstreetmap.josm.tools.I18n.tr;

public class TurnLanesEditorDialog extends ExtendedDialog {

    // Unique instance      
    static private TurnLanesEditorDialog instance = null;

    //constructor
    protected TurnLanesEditorDialog() {
        super(Main.parent, "", null, false, false);
        build();
    }

    static public TurnLanesEditorDialog getInstance() {
        if (instance == null) {
            instance = new TurnLanesEditorDialog();
        }
        return instance;
    }
    static public final Dimension PREFERRED_SIZE = new Dimension(750, 700);
    static public final Dimension MIN_SIZE = new Dimension(630, 600);
    private TagEditor tagEditor = null;
    private BuildTurnLanes buildTurnLanes = null;
    private JButton jbOk = null;
    private OKAction okAction = null;
    private CancelAction cancelAction = null;

    // Last Editions 
    List<BRoad> lastEdits = new ArrayList<>();

    protected void build() {
        //Parameters for Dialog
        getContentPane().setLayout(new BorderLayout());
        setModal(false);
        setSize(PREFERRED_SIZE);
        setTitle(tr("Turn Lanes Editor"));
        setLocation(90, 90);
        pack();
        // Preset Panel
        JPanel pnlPresetGrid = buildPresetGridPanel();
        pnlPresetGrid.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        // Tag Panel
        JPanel pnlTagGrid = buildTagGridPanel();
        //Split the Preset an dTag panel
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                pnlPresetGrid,
                pnlTagGrid
        );

        setMinimumSize(MIN_SIZE);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(380);
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(buildButtonRowPanel(), BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(cancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    //Build Buttons
    protected JPanel buildButtonRowPanel() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.CENTER));
        jbOk = new JButton(okAction = new OKAction());
        pnl.add(jbOk);
        getModel().addPropertyChangeListener(okAction);
        pnl.add(new JButton(cancelAction = new CancelAction()));
        return pnl;
    }

    // Build tag grid
    protected JPanel buildTagGridPanel() {
        tagEditor = new TagEditor();
        return tagEditor;
    }

    public TagEditorModel getModel() {
        return tagEditor.getModel();
    }

    // Build preset grid
    protected JPanel buildPresetGridPanel() {
        buildTurnLanes = new BuildTurnLanes();
        buildTurnLanes.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(BuildTurnLanes.ROADCHANGED)) {
                    addTagOnRoad((BRoad) evt.getNewValue());
                    jbOk.requestFocus();
                }

            }
        });
        return buildTurnLanes;
    }

    public PresetsTableModel getPressetTableModel() {
        return buildTurnLanes.getModel();
    }

    public void startEditSession() {
        tagEditor.getModel().clearAppliedPresets();
        tagEditor.getModel().initFromJOSMSelection();
        getModel().ensureOneTag();
        setRoadProperties();
    }

    //Buton Actions
    class CancelAction extends AbstractAction {

        public CancelAction() {
            putValue(NAME, tr("Cancel"));
            putValue(SMALL_ICON, ImageProvider.get("cancel"));
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            putValue(SHORT_DESCRIPTION, tr("Abort tag editing and close dialog"));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            setVisible(false);
        }
    }

    class OKAction extends AbstractAction implements PropertyChangeListener {

        public OKAction() {
            putValue(NAME, tr("OK"));
            putValue(SMALL_ICON, ImageProvider.get("ok"));
            putValue(SHORT_DESCRIPTION, tr("Apply edited tags and close dialog"));
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ctrl ENTER"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            run();
            // Add on table
            buildTurnLanes.addLastEditInTable();
            buildTurnLanes.clearSelection();
        }

        public void run() {
            tagEditor.stopEditing();
            setVisible(false);
            tagEditor.getModel().updateJOSMSelection();
            DataSet ds = Main.getLayerManager().getEditDataSet();
            ds.fireSelectionChanged();
            Main.parent.repaint(); // repaint all
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!evt.getPropertyName().equals(TagEditorModel.PROP_DIRTY)) {
                return;
            }
            if (!evt.getNewValue().getClass().equals(Boolean.class)) {
                return;
            }
            setEnabled(true);
        }
    }

    public boolean addOneway() {
        Collection<OsmPrimitive> selection = Main.getLayerManager().getEditDataSet().getSelected();
        for (OsmPrimitive element : selection) {
            if (element.hasDirectionKeys()) {
                return true;
            }
        }
        return false;
    }

    public void setRoadProperties() {
        //Set the selection Roads
        PresetsData presetsData = new PresetsData();
        BRoad bRoad = new BRoad();
        //set as unidirectional as first
        bRoad.setName("Unidirectional");
        Collection<OsmPrimitive> selection = Main.getLayerManager().getEditDataSet().getSelected();
        for (OsmPrimitive element : selection) {
            for (String key : element.keySet()) {
                //Unidirectional
                if (key.equals("turn:lanes")) {
                    bRoad.getLanesUnid().setStringLanes("unid", element.get(key));
                    bRoad.setName("Unidirectional");
                    if (element.hasKey("lanes") && Util.isInt(element.get("lanes")) && Integer.valueOf(element.get("lanes")) != bRoad.getLanesUnid().getLanes().size()) {
                        new Notification(tr(" The number of lanes has fixed according number of turns")).show();
                    }
                } else if (key.equals("lanes") && Util.isInt(element.get(key)) && !element.hasKey("turn:lanes") && element.hasDirectionKeys()) {
                    bRoad = presetsData.defaultRoadUnidirectional(Integer.valueOf(element.get(key)));
                    bRoad.setName("Unidirectional");
                } else if (key.equals("lanes") && Util.isInt(element.get(key)) && !element.hasKey("turn:lanes") && !(element.hasKey("turn:lanes:forward") || element.hasKey("turn:lanes:both_ways") || element.hasKey("turn:lanes:backward") || element.hasKey("lanes:forward") || element.hasKey("lanes:both_ways") || element.hasKey("lanes:backward"))) {
                    bRoad = presetsData.defaultRoadUnidirectional(Integer.valueOf(element.get(key)));
                    bRoad.setName("Unidirectional");
                } //Bidirectional
                else if (key.equals("turn:lanes:forward")) {
                    bRoad.getLanesA().setStringLanes("forward", element.get(key));
                    bRoad.getLanesA().setType("forward");
                    bRoad.setName("Bidirectional");
                    if (element.hasKey("lanes:forward") && Util.isInt(element.get("lanes:forward")) && Integer.valueOf(element.get("lanes:forward")) != bRoad.getLanesA().getLanes().size()) {
                        new Notification(tr(" The number of lanes:forward has fixed according number of turns")).show();
                    }
                } else if (key.equals("turn:lanes:both_ways")) {
                    bRoad.getLanesB().setStringLanes("both_ways", element.get(key));
                    bRoad.getLanesB().setType("both_ways");
                    bRoad.setName("Bidirectional");
                    if (element.hasKey("lanes:both_ways") && Util.isInt(element.get("lanes:both_ways")) && Integer.valueOf(element.get("lanes:both_ways")) != bRoad.getLanesB().getLanes().size()) {
                        new Notification(tr(" The number of lanes:both_ways has fixed according number of turns")).show();
                    }
                } else if (key.equals("turn:lanes:backward")) {
                    bRoad.getLanesC().setStringLanes("backward", element.get(key));
                    bRoad.getLanesC().setType("backward");
                    bRoad.setName("Bidirectional");
                    if (element.hasKey("lanes:backward") && Util.isInt(element.get("lanes:backward")) && Integer.valueOf(element.get("lanes:backward")) != bRoad.getLanesC().getLanes().size()) {
                        new Notification(tr(" The number of lanes:backward has fixed according number of turns")).show();
                    }
                } //in case the road has just lanes
                else if (key.equals("lanes:forward") && Util.isInt(element.get(key)) && !element.hasKey("turn:lanes:forward")) {
                    bRoad.setLanesA(presetsData.defaultLanes("forward", Integer.valueOf(element.get(key))));
                    bRoad.getLanesA().setType("forward");
                    bRoad.setName("Bidirectional");
                } else if (key.equals("lanes:both_ways") && Util.isInt(element.get(key)) && !element.hasKey("turn:lanes:both_ways")) {
                    bRoad.setLanesB(presetsData.defaultLanes("both_ways", Integer.valueOf(element.get(key))));
                    bRoad.getLanesB().setType("both_ways");
                    bRoad.setName("Bidirectional");
                } else if (key.equals("lanes:backward") && Util.isInt(element.get(key)) && !element.hasKey("turn:lanes:backward")) {
                    bRoad.setLanesC(presetsData.defaultLanes("backward", Integer.valueOf(element.get(key))));
                    bRoad.getLanesC().setType("backward");
                    bRoad.setName("Bidirectional");
                }
                //Notifications
                if (key.equals("oneway") && element.get(key).equals("-1")) {
                    new Notification(tr("check the right direction of the way")).show();
                }
            }
        }
        if (bRoad.getName().equals("Unidirectional")) {
            if (bRoad.getLanesUnid().getLanes().size() > 0) {
                buildTurnLanes.setLanesByRoadUnidirectional(bRoad);
            } else {
                //buildTurnLanes.startDefaultUnidirectional();
                buildTurnLanes.setLastEdit();
            }
        } else {
            if (bRoad.getLanesA().getLanes().size() > 0 || bRoad.getLanesB().getLanes().size() > 0 || bRoad.getLanesC().getLanes().size() > 0) {
                if (bRoad.getLanesA().getLanes().isEmpty()) {
                    bRoad.setLanesA(presetsData.defaultLanes("forward", 1));
                }
                if (bRoad.getLanesC().getLanes().isEmpty()) {
                    bRoad.setLanesC(presetsData.defaultLanes("backward", 1));
                }
                buildTurnLanes.setLanesByRoadBidirectional(bRoad);
            } else {
                //buildTurnLanes.startDefaultBidirectional();
                buildTurnLanes.setLastEdit();
            }
        }
    }

    public void addTagOnRoad(BRoad bRoad) {
        //Clear
        tagEditor.getModel().delete("turn:lanes");
        tagEditor.getModel().delete("lanes");
        tagEditor.getModel().delete("turn:lanes:forward");
        tagEditor.getModel().delete("lanes:forward");
        tagEditor.getModel().delete("turn:lanes:both_ways");
        tagEditor.getModel().delete("lanes:both_ways");
        tagEditor.getModel().delete("turn:lanes:backward");
        tagEditor.getModel().delete("lanes:backward");

        if (bRoad.getName().equals("Unidirectional")) {
            if (Util.isEmptyturnlane(bRoad.getLanesUnid().getTagturns())) {
                if (bRoad.isNone()) {
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes", Util.setNoneOnEmpty(bRoad.getLanesUnid().getTagturns())));
                } else {
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes", bRoad.getLanesUnid().getTagturns()));
                }
            }
            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes", String.valueOf(bRoad.getLanesUnid().getLanes().size())));
        } else {
            if (!bRoad.getLanesA().getLanes().isEmpty()) {
                if (bRoad.getLanesA().getType().equals("forward")) {
                    if (Util.isEmptyturnlane(bRoad.getLanesA().getTagturns())) {
                        if (bRoad.isNone()) {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:forward", Util.setNoneOnEmpty(bRoad.getLanesA().getTagturns())));
                        } else {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:forward", bRoad.getLanesA().getTagturns()));
                        }
                    }
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes:forward", String.valueOf(bRoad.getLanesA().getLanes().size())));
                } else {
                    if (Util.isEmptyturnlane(bRoad.getLanesA().getTagturns())) {
                        if (bRoad.isNone()) {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:backward", Util.setNoneOnEmpty(bRoad.getLanesA().getTagturns())));
                        } else {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:backward", bRoad.getLanesA().getTagturns()));
                        }
                    }
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes:backward", String.valueOf(bRoad.getLanesA().getLanes().size())));
                }
            }
            if (!bRoad.getLanesB().getLanes().isEmpty()) {
                if (Util.isEmptyturnlane(bRoad.getLanesB().getTagturns())) {
                    if (bRoad.isNone()) {
                        tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:both_ways", Util.setNoneOnEmpty(bRoad.getLanesB().getTagturns())));
                    } else {
                        tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:both_ways", bRoad.getLanesB().getTagturns()));
                    }
                }
                tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes:both_ways", String.valueOf(bRoad.getLanesB().getLanes().size())));
            }
            if (!bRoad.getLanesC().getLanes().isEmpty()) {
                if (bRoad.getLanesC().getType().equals("backward")) {
                    if (Util.isEmptyturnlane(bRoad.getLanesC().getTagturns())) {
                        if (bRoad.isNone()) {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:backward", Util.setNoneOnEmpty(bRoad.getLanesC().getTagturns())));
                        } else {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:backward", bRoad.getLanesC().getTagturns()));
                        }
                    }
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes:backward", String.valueOf(bRoad.getLanesC().getLanes().size())));
                } else {
                    if (Util.isEmptyturnlane(bRoad.getLanesC().getTagturns())) {
                        if (bRoad.isNone()) {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:forward", Util.setNoneOnEmpty(bRoad.getLanesC().getTagturns())));
                        } else {
                            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("turn:lanes:forward", bRoad.getLanesC().getTagturns()));
                        }
                    }
                    tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes:forward", String.valueOf(bRoad.getLanesC().getLanes().size())));
                }
            }
            tagEditor.getModel().applyKeyValuePair(new KeyValuePair("lanes", String.valueOf(bRoad.getNumLanesBidirectional())));
        }
        tagEditor.repaint();
    }

    public void setEnableOK(boolean active) {
        jbOk.setEnabled(active);
    }
}
