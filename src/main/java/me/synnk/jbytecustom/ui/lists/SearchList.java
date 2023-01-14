package me.synnk.jbytecustom.ui.lists;

import me.synnk.jbytecustom.JByteCustom;
import me.synnk.jbytecustom.ui.lists.entries.SearchEntry;
import me.synnk.jbytecustom.utils.list.LazyListModel;
import me.synnk.jbytecustom.utils.task.search.LdcTask;
import me.synnk.jbytecustom.utils.task.search.ReferenceTask;
import me.synnk.jbytecustom.utils.task.search.SFTask;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

public class SearchList extends JList<SearchEntry> {

    private final JByteCustom jbm;

    public SearchList(JByteCustom jbm) {
        super(new LazyListModel<>());
        this.jbm = jbm;
        this.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem decl = new JMenuItem(JByteCustom.res.getResource("go_to_dec"));
                    decl.addActionListener(e1 -> {
                        ClassNode cn = SearchList.this.getSelectedValue().getCn();
                        MethodNode mn = SearchList.this.getSelectedValue().getMn();
                        jbm.selectMethod(cn, mn);
                    });
                    menu.add(decl);
                    JMenuItem treeEntry = new JMenuItem(JByteCustom.res.getResource("select_tree"));
                    treeEntry.addActionListener(e12 -> {
                        MethodNode mn = SearchList.this.getSelectedValue().getMn();
                        jbm.treeSelection(mn);
                    });
                    menu.add(treeEntry);
                    JMenuItem copy = new JMenuItem(JByteCustom.res.getResource("copy_text"));
                    copy.addActionListener(e13 -> {
                        StringSelection selection = new StringSelection(SearchList.this.getSelectedValue().getFound());
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    });
                    menu.add(copy);
                    menu.show(SearchList.this, e.getX(), e.getY());
                }
            }
        });
        this.setPrototypeCellValue(new SearchEntry());
    }

    public void searchForConstant(String ldc, boolean exact, boolean cs, boolean regex) {
        new LdcTask(this, jbm, ldc, exact, cs, regex).execute();
    }

    public void searchForPatternRegex(Pattern p) {
        new LdcTask(this, jbm, p).execute();
    }

    public void searchForFMInsn(String owner, String name, String desc, boolean exact, boolean field) {
        new ReferenceTask(this, jbm, owner, name, desc, exact, field).execute();
    }

    public void searchForSF(String sf) {
        new SFTask(this, jbm, sf).execute();
    }
}
