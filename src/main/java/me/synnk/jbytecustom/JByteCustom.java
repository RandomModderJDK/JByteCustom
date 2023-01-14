package me.synnk.jbytecustom;

import com.sun.tools.attach.VirtualMachine;
import me.lpk.util.ASMUtils;
import me.lpk.util.OpUtils;
import me.synnk.jbytecustom.decompiler.DecompilerOutput;
import me.synnk.jbytecustom.discord.Discord;
import me.synnk.jbytecustom.logging.Logging;
import me.synnk.jbytecustom.res.LanguageRes;
import me.synnk.jbytecustom.res.Options;
import me.synnk.jbytecustom.ui.*;
import me.synnk.jbytecustom.ui.graph.ControlFlowPanel;
import me.synnk.jbytecustom.ui.lists.*;
import me.synnk.jbytecustom.ui.tree.SortedTreeNode;
import me.synnk.jbytecustom.utils.*;
import me.synnk.jbytecustom.utils.asm.FrameGen;
import me.synnk.jbytecustom.utils.attach.RuntimeJarArchive;
import me.synnk.jbytecustom.utils.gui.LookUtils;
import me.synnk.jbytecustom.utils.task.*;
import org.apache.commons.cli.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class JByteCustom extends JFrame {
    public final static String version = "1.1.8";
    private static final String jbytemod = "JByteCustom" + " " + version;

    public static File workingDir = new File(".");
    public static String configPath = "jbytemod.cfg";
    public static Logging LOGGER;
    public static LanguageRes res;
    public static Options ops;
    public static String lastEditFile = "";
    public static HashMap<ClassNode, MethodNode> lastSelectedTreeEntries = new LinkedHashMap<>();
    public static JByteCustom instance;
    public static Color border;
    private static boolean lafInit;
    private static JarArchive file;
    private static Instrumentation agentInstrumentation;

    static {
        try {
            System.loadLibrary("attach");
        } catch (Throwable ignored) {

        }
    }

    private final ClassTree jarTree;
    private MyCodeList clist;
    private final PageEndPanel pp;
    private SearchList slist;
    private DecompilerPanel dp;
    private TCBList tcblist;
    private MyTabbedPane tabbedPane;
    private InfoPanel sp;
    private LVPList lvplist;
    private ControlFlowPanel cfp;
    private ClassNode currentNode;
    private MethodNode currentMethod;
    private File filePath;

    /**
     * Create the frame.
     */
    public JByteCustom(boolean agent) throws Exception {
        if (ops.get("use_rt").getBoolean()) {
            new FrameGen().start();
        }
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (JOptionPane.showConfirmDialog(JByteCustom.this, res.getResource("exit_warn"), res.getResource("is_sure"),
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    if (agent) {
                        dispose();
                    } else {
                        Discord.discordRPC.Discord_Shutdown();
                        Runtime.getRuntime().exit(0);
                    }
                }
            }
        });
        if (border == null) {
            border = new Color(146, 151, 161);
        }
        // Start on the middle of the screen
        this.setSize(1024, 640);
        this.setLocationRelativeTo(null);

        this.setTitle(jbytemod);
        this.setIconImage(Toolkit.getDefaultToolkit().getImage(TreeCellRenderer.class.getResource("/resources/jbytemod.png")));
        this.setJMenuBar(new MyMenuBar(this, agent));
        this.jarTree = new ClassTree(this);
        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(5, 5));
        this.setContentPane(contentPane);
        this.setTCBList(new TCBList());
        this.setLVPList(new LVPList());
        JPanel border = new JPanel();
        border.setLayout(new GridLayout());
        JSplitPane splitPane = new MySplitPane(this, jarTree);
        JPanel b2 = new JPanel();
        b2.setBorder(new EmptyBorder(5, 0, 5, 0));
        b2.setLayout(new GridLayout());
        b2.add(splitPane);
        border.add(b2);
        contentPane.add(border, BorderLayout.CENTER);
        contentPane.add(pp = new PageEndPanel(), BorderLayout.PAGE_END);
        contentPane.add(new MyToolBar(this), BorderLayout.PAGE_START);
        if (file != null) {
            this.refreshTree();
        }
    }

    public static void agentmain(String agentArgs, Instrumentation ins) throws Exception {
        if (!ins.isRedefineClassesSupported()) {
            JOptionPane.showMessageDialog(null, "Class redefinition is disabled, cannot attach!");
            return;
        }
        agentInstrumentation = ins;
        workingDir = new File(agentArgs);
        initialize();
        if (!lafInit) {
            LookUtils.setLAF();
            lafInit = true;
        }
        JByteCustom.file = new RuntimeJarArchive(ins);
        JByteCustom frame = new JByteCustom(true);
        frame.setTitleSuffix("Agent");
        instance = frame;
        frame.setVisible(true);
    }

    public static void initialize() {
        LOGGER = new Logging();
        res = new LanguageRes();
        ops = new Options();
        Discord.init();
        try {
            System.setProperty("file.encoding", "UTF-8");
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null, null);
        } catch (Throwable t) {
            JByteCustom.LOGGER.err("Failed to set encoding to UTF-8 (" + t.getMessage() + ")");
        }
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        options.addOption("f", "file", true, "File to open");
        options.addOption("d", "dir", true, "Working directory");
        options.addOption("c", "config", true, "Config file name");
        options.addOption("?", "help", false, "Prints this help");

        CommandLineParser parser = new DefaultParser();
        CommandLine line;

        try {
            line = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            e.printStackTrace();
            throw new RuntimeException("An error occurred while parsing the commandline ");
        }
        if (line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(jbytemod, options);
            return;
        }
        if (line.hasOption("d")) {
            workingDir = new File(line.getOptionValue("d"));
            if (!(workingDir.exists() && workingDir.isDirectory())) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(jbytemod, options);
                return;
            }
            JByteCustom.LOGGER.err("Specified working dir set");
        }
        if (line.hasOption("c")) {
            configPath = line.getOptionValue("c");
        }
        initialize();
        EventQueue.invokeLater( () -> {
            try {
                if (!lafInit) {
                    LookUtils.setLAF();
                    lafInit = true;
                }
                JByteCustom frame = new JByteCustom(false);

                instance = frame;
                frame.setVisible(true);
                if (line.hasOption("f")) {
                    File input = new File(line.getOptionValue("f"));
                    if (FileUtils.exists(input) && FileUtils.isType(input, ".jar", ".class")) {
                        frame.loadFile(input);
                        JByteCustom.LOGGER.log("Specified file loaded");
                    } else {
                        JByteCustom.LOGGER.err("Specified file not found");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void resetLAF() {
        lafInit = false;
    }

    public static void restartGUI() {
        instance.dispose();
        instance = null;
        System.gc();
        JByteCustom.main(new String[0]);
    }

    public void applyChangesAgent() {
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        new RetransformTask(this, agentInstrumentation, file).execute();
    }

    public void attachTo(VirtualMachine vm) {
        new AttachTask(this, vm).execute();
    }

    public ControlFlowPanel getCFP() {
        return this.cfp;
    }

    public void setCFP(ControlFlowPanel cfp) {
        this.cfp = cfp;
    }

    public MyCodeList getCodeList() {
        return clist;
    }

    public void setCodeList(MyCodeList list) {
        this.clist = list;
    }

    public MethodNode getCurrentMethod() {
        return currentMethod;
    }

    public ClassNode getCurrentNode() {
        return currentNode;
    }

    public JarArchive getFile() {
        return file;
    }

    public File getFilePath() {
        return filePath;
    }

    public ClassTree getJarTree() {
        return jarTree;
    }

    public LVPList getLVPList() {
        return lvplist;
    }

    private void setLVPList(LVPList lvp) {
        this.lvplist = lvp;
    }

    public PageEndPanel getPP() {
        return pp;
    }

    public SearchList getSearchList() {
        return slist;
    }

    public MyTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public void setTabbedPane(MyTabbedPane tp) {
        this.tabbedPane = tp;
    }

    public TCBList getTCBList() {
        return tcblist;
    }

    public void setTCBList(TCBList tcb) {
        this.tcblist = tcb;
    }

    /**
     * Load .jar or .class file
     */
    public void loadFile(File input) {
        this.filePath = input;
        String ap = input.getAbsolutePath();
        if (ap.endsWith(".jar")) {
            try {
                file = new JarArchive(this, input);
                this.setTitleSuffix(input.getName());
            } catch (Throwable e) {
                new ErrorDisplay(e);
            }
        } else if (ap.endsWith(".class")) {
            try {
                file = new JarArchive(ASMUtils.getNode(Files.readAllBytes(input.toPath())));
                this.setTitleSuffix(input.getName());
                this.refreshTree();
            } catch (Throwable e) {
                new ErrorDisplay(e);
            }
        } else {
            new ErrorDisplay(new UnsupportedOperationException(res.getResource("jar_warn")));
        }
    }

    public void refreshAgentClasses() {
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        this.refreshTree();
    }

    // Function to Save a File.
    // Author: SynnK
    // Arguments: path, filename, content, suggestname, extensions

    public void extractFile(String path, String fileName, String fileContent, String suggestName, String extension) {
        String location;
        if (DecompilerOutput.decompiledClassName == null) {
            JOptionPane.showMessageDialog(null, "No Class is selected!");
        } else {
            JFileChooser jfc = new JFileChooser(new File(path));

            jfc.setSelectedFile(new File(suggestName + ".java"));
            jfc.setAcceptAllFileFilterUsed(false);

            jfc.setFileFilter(new FileNameExtensionFilter("Java File (*.java)", extension));

            int result = jfc.showSaveDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                location = jfc.getSelectedFile().getPath();
                // Prevent null extension
                if (!jfc.getSelectedFile().getName().endsWith(".java")) {
                    location = location + ".java";
                }
                if (FileUtils.exists(new File(location))) {
                    LOGGER.err("File already exists.");
                } else {
                    JByteCustom.LOGGER.log("Selected output file: " + location);
                    try (FileWriter writer = new FileWriter(location);
                         BufferedWriter bw = new BufferedWriter(writer)) {
                        bw.write(fileContent);
                    } catch (IOException err) {
                        System.err.format("IOException: %s%n", err);
                    } finally {
                        JByteCustom.LOGGER.log("Extracted class '" + fileName + "' to " + location);
                    }
                }
            }
        }
    }

    public void refreshTree() {
        LOGGER.log("Building tree..");
        this.jarTree.refreshTree(file);
    }

    public void saveFile(File output) {
        try {
            new SaveTask(this, output, file).execute();
        } catch (Throwable t) {
            new ErrorDisplay(t);
        }
    }

    public void selectClass(ClassNode cn) {
        DecompilerOutput.decompiledClassName = cn.name;
        if (ops.get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        this.currentNode = cn;
        this.currentMethod = null;
        sp.selectClass(cn);
        clist.loadFields(cn);
        tabbedPane.selectClass(cn);
        lastSelectedTreeEntries.put(cn, null);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    private boolean selectEntry(MethodNode mn, DefaultTreeModel tm, SortedTreeNode node) {
        for (int i = 0; i < tm.getChildCount(node); i++) {
            SortedTreeNode child = (SortedTreeNode) tm.getChild(node, i);
            if (child.getMn() != null && child.getMn().equals(mn)) {
                TreePath tp = new TreePath(tm.getPathToRoot(child));
                jarTree.setSelectionPath(tp);
                jarTree.scrollPathToVisible(tp);
                return true;
            }
            if (!child.isLeaf()) {
                if (selectEntry(mn, tm, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        if (ops.get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        OpUtils.clearLabelCache();
        this.currentNode = cn;
        this.currentMethod = mn;
        sp.selectMethod(cn, mn);
        if (!clist.loadInstructions(mn)) {
            clist.setSelectedIndex(-1);
        }
        tcblist.addNodes(cn, mn);
        lvplist.addNodes(cn, mn);
        cfp.setNode(mn);
        dp.setText("");
        tabbedPane.selectMethod(cn, mn);
        lastSelectedTreeEntries.put(cn, mn);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    public void setDP(DecompilerPanel dp) {
        this.dp = dp;
    }

    public void setSearchlist(SearchList searchList) {
        this.slist = searchList;
    }

    public void setSP(InfoPanel sp) {
        this.sp = sp;
    }

    private void setTitleSuffix(String suffix) {
        this.setTitle(jbytemod + " - " + suffix);
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
    }

    public void treeSelection(MethodNode mn) {
        //selection may take some time
        new Thread(() -> {
            DefaultTreeModel tm = (DefaultTreeModel) jarTree.getModel();
            if (this.selectEntry(mn, tm, (SortedTreeNode) tm.getRoot())) {
                jarTree.repaint();
            }
        }).start();
    }

}
