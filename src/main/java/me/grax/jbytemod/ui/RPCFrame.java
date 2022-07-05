package me.grax.jbytemod.ui;

import me.grax.jbytemod.JByteMod;

import javax.swing.*;
import java.awt.*;

public class RPCFrame extends JDialog {

    public static JLabel labelState = new JLabel("State: ");
    public static JLabel labelDetails = new JLabel("Details: ");
    public static JTextField fieldState = new JTextField(20);
    public static JTextField fieldDetails = new JTextField(20);
    public static JCheckBox checker = new JCheckBox("Editable");
    public static JCheckBox checkerTwo = new JCheckBox("Editable");
    public static JButton buttonLogin = new JButton("Apply");


    public RPCFrame(JByteMod jbm) {

        this.setTitle("RPC Changer");
        // create a new panel with GridBagLayout manager
        JPanel newPanel = new JPanel(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);

        // details
        constraints.gridx = 0;
        constraints.gridy = 0;
        newPanel.add(labelDetails, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        newPanel.add(fieldDetails, constraints);

        // state
        constraints.gridx = 0;
        constraints.gridy = 1;
        newPanel.add(labelState, constraints);

        constraints.gridx = 1;
        newPanel.add(fieldState, constraints);

        // button
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.CENTER;
        newPanel.add(buttonLogin, constraints);

        // set border for the panel
        newPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Discord RPC"));

        // add the panel to this frame
        add(newPanel);

        pack();
        setLocationRelativeTo(null);
    }
}

