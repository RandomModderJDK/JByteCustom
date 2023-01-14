package me.synnk.jbytecustom.ui.lists;

import me.synnk.jbytecustom.JByteCustom;
import me.synnk.jbytecustom.ui.frames.JAnnotationEditor;
import me.synnk.jbytecustom.ui.JSearch;
import me.synnk.jbytecustom.ui.dialogue.InsnEditDialogue;
import me.synnk.jbytecustom.ui.lists.entries.FieldEntry;
import me.synnk.jbytecustom.ui.lists.entries.InstrEntry;
import me.synnk.jbytecustom.ui.lists.entries.PrototypeEntry;
import me.synnk.jbytecustom.utils.ErrorDisplay;
import me.synnk.jbytecustom.utils.HtmlSelection;
import me.synnk.jbytecustom.utils.list.LazyListModel;
import me.lpk.util.OpUtils;
import org.objectweb.asm.tree.*;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.List;

public class MyCodeList extends JList<InstrEntry> {
    private final JLabel editor;
    private AdressList adressList;
    private ErrorList errorList;
    private MethodNode currentMethod;
    private ClassNode currentClass;

    public MyCodeList(JByteCustom jam, JLabel editor) {
        super(new LazyListModel<>());
        this.editor = editor;
        this.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        this.setFocusable(false);
        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                InstrEntry entry = MyCodeList.this.getSelectedValue();
                if (entry == null) {
                    createPopupForEmptyList(jam);
                    return;
                }
                MethodNode mn = entry.getMethod();
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (mn != null) {
                        AbstractInsnNode ain = entry.getInstr();
                        rightClickMethod(jam, mn, ain, MyCodeList.this.getSelectedValuesList());
                    } else {
                        rightClickField(jam, (FieldEntry) entry, MyCodeList.this.getSelectedValuesList());
                    }
                } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    if (mn != null) {
                        try {
                            if (InsnEditDialogue.canEdit(entry.getInstr())) {
                                new InsnEditDialogue(mn, entry.getInstr()).open();
                            }
                        } catch (Exception e1) {
                            new ErrorDisplay(e1);
                        }
                    } else {
                        FieldEntry fe = (FieldEntry) entry;
                        try {
                            new InsnEditDialogue(null, fe.getFn()).open();
                        } catch (Exception e1) {
                            new ErrorDisplay(e1);
                        }
                        MyCodeList.this.loadFields(fe.getCn());
                    }
                }
            }
        });
        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK), "search");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK), "copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK), "duplicate");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK), "insert");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), "insert");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "down");
        am.put("search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new JSearch(MyCodeList.this).setVisible(true);
            }
        });

        am.put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard();
            }
        });

        am.put("duplicate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InstrEntry entry = getSelectedValue();
                if (entry != null && entry.getMethod() != null) {
                    duplicate(entry.getMethod(), entry.getInstr());
                }
            }
        });
        am.put("insert", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InstrEntry entry = getSelectedValue();
                if (entry != null && entry.getMethod() != null) {
                    try {
                        InsnEditDialogue.createInsertInsnDialog(entry.getMethod(), entry.getInstr(), true);
                        OpUtils.clearLabelCache();
                    } catch (Exception e1) {
                        new ErrorDisplay(e1);
                    }
                }
            }
        });
        am.put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<InstrEntry> entries = getSelectedValuesList();
                for (InstrEntry entry : entries) {
                    if (entry.getMethod() != null) {
                        removeNode(entry.getMethod(), entry.getInstr());
                    }
                }
            }
        });
        am.put("up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InstrEntry entry = getSelectedValue();
                if (entry != null && entry.getMethod() != null) {
                    int index = getSelectedIndex();
                    if (moveUp(entry.getMethod(), entry.getInstr())) {
                        setSelectedIndex(index - 1);
                    }
                }
            }
        });
        am.put("down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InstrEntry entry = getSelectedValue();
                if (entry != null && entry.getMethod() != null) {
                    int index = getSelectedIndex();
                    if (moveDown(entry.getMethod(), entry.getInstr())) {
                        setSelectedIndex(index + 1);
                    }
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (JByteCustom.ops.get("hints").getBoolean()) {
                    ListModel<InstrEntry> m = getModel();
                    int index = locationToIndex(e.getPoint());
                    if (index > -1) {
                        InstrEntry el = m.getElementAt(index);
                        setToolTipText(el.getHint());
                    }
                } else {
                    setToolTipText(null);
                }
            }
        });
        this.setPrototypeCellValue(new PrototypeEntry());
        this.setFixedCellWidth(-1);
    }

    protected void rightClickField(JByteCustom jbm, FieldEntry fle, List<InstrEntry> selected) {
        ClassNode cn = fle.getCn();
        JPopupMenu menu = new JPopupMenu();
        if (selected.size() > 1) {
            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove_all"));
            remove.addActionListener((e) -> {
                    for (InstrEntry sel : selected) {
                        cn.fields.remove(((FieldEntry) sel).getFn());
                    }
                    MyCodeList.this.loadFields(cn);

            });
            menu.add(remove);
            menu.add(copyText());
            menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
        } else {
            JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("edit"));
            edit.addActionListener((event)-> {
                try {
                    new InsnEditDialogue(null, fle.getFn()).open();
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }
                MyCodeList.this.loadFields(cn);
            });
            menu.add(edit);
            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove"));
            remove.addActionListener(e -> {
                cn.fields.remove(fle.getFn());
                MyCodeList.this.loadFields(cn);
            });
            menu.add(remove);
            JMenuItem add = new JMenuItem(JByteCustom.res.getResource("insert"));
            add.addActionListener(e -> {
                try {
                    FieldNode fn = new FieldNode(1, "", "", "", null);
                    if (new InsnEditDialogue(null, fn).open()) {
                        cn.fields.add(fn);
                    }
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }
                MyCodeList.this.loadFields(cn);
            });
            menu.add(add);
            menu.add(copyText());
            JMenuItem annotations = new JMenuItem("Edit Annotations");
            annotations.addActionListener(e -> {
                if (!JAnnotationEditor.isOpen("visibleAnnotations"))
                    new JAnnotationEditor("Annotations", fle.getFn(), "visibleAnnotations").setVisible(true);
            });
            menu.add(annotations);
            JMenuItem invisAnnotations = new JMenuItem("Edit Invis Annotations");
            invisAnnotations.addActionListener(e -> {
                if (!JAnnotationEditor.isOpen("invisibleAnnotations"))
                    new JAnnotationEditor("Invis Annotations", fle.getFn(), "invisibleAnnotations").setVisible(true);
            });
            menu.add(invisAnnotations);
            menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
        }
    }

    private JMenuItem copyText() {
        JMenuItem copy = new JMenuItem(JByteCustom.res.getResource("copy_text"));
        copy.addActionListener(e -> {
            copyToClipboard();
            JByteCustom.LOGGER.log("Copied code to clipboard!");
        });
        return copy;
    }

    protected void copyToClipboard() {
        StringBuilder sb = new StringBuilder();
        boolean html = JByteCustom.ops.get("copy_formatted").getBoolean();
        if (html) {
            for (InstrEntry sel : MyCodeList.this.getSelectedValuesList()) {
                sb.append(sel.toString());
                sb.append("<br>");
            }
        } else {
            for (InstrEntry sel : MyCodeList.this.getSelectedValuesList()) {
                sb.append(sel.toEasyString());
                sb.append("\n");
            }
        }
        if (sb.length() > 0) {
            HtmlSelection selection = new HtmlSelection(sb.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    protected void rightClickMethod(JByteCustom jbm, MethodNode mn, AbstractInsnNode ain, List<InstrEntry> selected) {
        if (selected.size() > 1) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove_all"));
            remove.addActionListener(e -> {
                for (InstrEntry sel : selected) {
                    mn.instructions.remove(sel.getInstr());
                }
                OpUtils.clearLabelCache();
                MyCodeList.this.loadInstructions(mn);
            });
            menu.add(remove);
            menu.add(copyText());
            addPopupListener(menu);
            menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
        } else {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem insertBefore = new JMenuItem(JByteCustom.res.getResource("ins_before"));
            insertBefore.addActionListener(e -> {
                try {
                    InsnEditDialogue.createInsertInsnDialog(mn, ain, false);
                    OpUtils.clearLabelCache();
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }
            });
            menu.add(insertBefore);
            JMenuItem insert = new JMenuItem(JByteCustom.res.getResource("ins_after"));
            insert.addActionListener(e -> {
                try {
                    InsnEditDialogue.createInsertInsnDialog(mn, ain, true);
                    OpUtils.clearLabelCache();
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }
            });
            insert.setAccelerator(KeyStroke.getKeyStroke('I', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            menu.add(insert);

            if (InsnEditDialogue.canEdit(ain)) {
                JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("edit"));
                edit.addActionListener(e -> {
                    try {
                        new InsnEditDialogue(mn, ain).open();
                    } catch (Exception e1) {
                        new ErrorDisplay(e1);
                    }
                });
                menu.add(edit);
            }
            if (ain instanceof JumpInsnNode) {
                JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("jump_to_label"));
                edit.addActionListener(e -> {
                    JumpInsnNode jin = (JumpInsnNode) ain;
                    ListModel<InstrEntry> model = getModel();
                    for (int i = 0; i < model.getSize(); i++) {
                        InstrEntry sel = model.getElementAt(i);
                        if (sel.getInstr().equals(jin.label)) {
                            setSelectedIndex(i);
                            ensureIndexIsVisible(i);
                            break;
                        }
                    }
                });
                menu.add(edit);
            }
            if (ain instanceof MethodInsnNode) {
                JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("go_to_dec"));
                JMenuItem find_usage = new JMenuItem(JByteCustom.res.getResource("find_usage"));
                edit.addActionListener(e -> {
                    MethodInsnNode min = (MethodInsnNode) ain;
                    for (ClassNode cn : jbm.getFile().getClasses().values()) {

                        if (cn.name.equals(min.owner)) {
                            for (MethodNode mn1 : cn.methods) {
                                if (min.name.equals(mn1.name) && min.desc.equals(mn1.desc)) {
                                    jbm.selectMethod(cn, mn1);
                                    jbm.treeSelection(mn1);
                                    return;
                                }
                            }
                        }
                    }
                });

                find_usage.addActionListener(e -> jbm.getSearchList().searchForFMInsn(((MethodInsnNode) ain).owner, ((MethodInsnNode) ain).name, ((MethodInsnNode) ain).desc, true, false));

                menu.add(edit);
                menu.add(find_usage);
            }
            if (ain instanceof FieldInsnNode) {
                JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("go_to_dec"));
                JMenuItem find_usage = new JMenuItem(JByteCustom.res.getResource("find_usage"));
                edit.addActionListener(e -> {
                    FieldInsnNode fin = (FieldInsnNode) ain;
                    for (ClassNode cn : jbm.getFile().getClasses().values()) {
                        if (cn.name.equals(fin.owner)) {
                            jbm.selectClass(cn);
                            return;
                        }
                    }
                });

                find_usage.addActionListener(e -> jbm.getSearchList().searchForFMInsn(((FieldInsnNode) ain).owner, ((FieldInsnNode) ain).name, ((FieldInsnNode) ain).desc, true, true));

                menu.add(edit);
                menu.add(find_usage);
            }
            JMenuItem duplicate = new JMenuItem(JByteCustom.res.getResource("duplicate"));
            duplicate.addActionListener(e -> duplicate(mn, ain));
            duplicate.setAccelerator(KeyStroke.getKeyStroke('D', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            menu.add(duplicate);
            JMenuItem up = new JMenuItem(JByteCustom.res.getResource("move_up"));
            up.addActionListener(e -> moveUp(mn, ain));
            up.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
            menu.add(up);
            JMenuItem down = new JMenuItem(JByteCustom.res.getResource("move_down"));
            down.addActionListener(e -> moveDown(mn, ain));
            down.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
            menu.add(down);
            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove"));
            remove.addActionListener(e -> removeNode(mn, ain));
            remove.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
            menu.add(copyText());
            menu.add(remove);
            addPopupListener(menu);
            menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
        }
    }

    protected void removeNode(MethodNode mn, AbstractInsnNode ain) {
        mn.instructions.remove(ain);
        OpUtils.clearLabelCache();
        loadInstructions(mn);
    }

    protected boolean moveDown(MethodNode mn, AbstractInsnNode ain) {
        AbstractInsnNode node = ain.getNext();
        if (node != null) {
            mn.instructions.remove(node);
            mn.instructions.insertBefore(ain, node);
            OpUtils.clearLabelCache();
            loadInstructions(mn);
            return true;
        }
        return false;
    }

    protected boolean moveUp(MethodNode mn, AbstractInsnNode ain) {
        AbstractInsnNode node = ain.getPrevious();
        if (node != null) {
            mn.instructions.remove(node);
            mn.instructions.insert(ain, node);
            OpUtils.clearLabelCache();
            loadInstructions(mn);
            return true;
        }
        return false;
    }

    protected void duplicate(MethodNode mn, AbstractInsnNode ain) {
        try {
            if (ain instanceof LabelNode) {
                mn.instructions.insert(ain, new LabelNode());
                OpUtils.clearLabelCache();
            } else if (ain instanceof JumpInsnNode) {
                mn.instructions.insert(ain, new JumpInsnNode(ain.getOpcode(), ((JumpInsnNode) ain).label));
            } else {
                mn.instructions.insert(ain, ain.clone(new HashMap<>()));
            }
            MyCodeList.this.loadInstructions(mn);

        } catch (Exception e1) {
            new ErrorDisplay(e1);
        }
    }

    protected void createPopupForEmptyList(JByteCustom jbm) {
        JPopupMenu menu = new JPopupMenu();
        if (currentMethod != null) {
            JMenuItem add = new JMenuItem(JByteCustom.res.getResource("add"));
            add.addActionListener(e -> {
                try {
                    InsnEditDialogue.createInsertInsnDialog(currentMethod, null, true);
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }

            });
            menu.add(add);
        } else if (currentClass != null) {
            JMenuItem add = new JMenuItem(JByteCustom.res.getResource("add"));
            add.addActionListener(e -> {
                try {
                    FieldNode fn = new FieldNode(1, "", "", "", null);
                    if (new InsnEditDialogue(null, fn).open()) {
                        currentClass.fields.add(fn);
                    }
                } catch (Exception e1) {
                    new ErrorDisplay(e1);
                }
                MyCodeList.this.loadFields(currentClass);
            });
            menu.add(add);
        }
        try {
            menu.show(jbm, (int) jbm.getMousePosition().getX(), (int) jbm.getMousePosition().getY());
        } catch (NullPointerException exception) {
            JByteCustom.LOGGER.println("Null mouse position, weird. :/");
        }

    }

    protected void addPopupListener(JPopupMenu menu) {
        menu.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {
                MyCodeList.this.setFocusable(true);
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {
                MyCodeList.this.setFocusable(true);
            }

            public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent) {
                MyCodeList.this.setFocusable(false);
            }
        });
    }

    public boolean loadInstructions(MethodNode m) {
        this.currentMethod = m;
        this.currentClass = null;
        LazyListModel<InstrEntry> lm = new LazyListModel<>();
        editor.setText(m.name + m.desc);
        for (AbstractInsnNode i : m.instructions) {
            InstrEntry entry = new InstrEntry(m, i);
            lm.addElement(entry);
        }
        this.setModel(lm);
        //update sidebar
        if (adressList != null) {
            adressList.updateAdr();
        }
        if (errorList != null) {
            errorList.updateErrors();
        }
        return true;
    }

    public void setAddressList(AdressList adressList) {
        this.adressList = adressList;
    }

    public void loadFields(ClassNode cn) {
        this.currentClass = cn;
        this.currentMethod = null;
        LazyListModel<InstrEntry> lm = new LazyListModel<>();
        editor.setText(cn.name + " Fields");
        // A cada FieldNode entre cn.fields
        for (FieldNode fn : cn.fields) {
            InstrEntry entry = new FieldEntry(cn, fn);
            lm.addElement(entry);
        }
        this.setModel(lm);
        //update sidebar
        if (adressList != null) {
            adressList.updateAdr();
        }
        if (errorList != null) {
            errorList.updateErrors();
        }
    }

    public void setErrorList(ErrorList errorList) {
        this.errorList = errorList;
    }
}