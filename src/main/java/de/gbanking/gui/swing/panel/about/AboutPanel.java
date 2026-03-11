package de.gbanking.gui.swing.panel.about;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

public class AboutPanel extends JPanel {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3619484956548128259L;
	
	private JFrame parentFrame;
	private JDialog modelDialog;
	
	JButton cancelButton;
	
	public AboutPanel(JFrame parent) {
		this.parentFrame = parent;
	}
	
	public JDialog createNewAboutWindow() {
		modelDialog = new JDialog(parentFrame, "Über GBanking", Dialog.ModalityType.DOCUMENT_MODAL);
		modelDialog.setBounds(132, 132, 300, 200);
		
		createAboutPanel();

		return modelDialog;
	}
	
	private void createAboutPanel() {
		
		Container dialogContainer = modelDialog.getContentPane();
		
		GridBagLayout aboutPanelLayout = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		JPanel panel1 = new JPanel();
		panel1.setLayout(aboutPanelLayout);
		Border panel1Border = BorderFactory.createTitledBorder("Über..."); 
		panel1.setBorder(panel1Border);
		
		JLabel info01l = new JLabel("Informationen ...... 1");
		JLabel info02 = new JLabel("Informationen ......  2");
		JLabel info03 = new JLabel("Informationen ......  3");
		JLabel info04 = new JLabel("Informationen ......  4");
		
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel1.add(info01l, gbc);
		gbc.gridy = 1;
		panel1.add(info02, gbc);
		gbc.gridy = 2;
		panel1.add(info03, gbc);
		gbc.gridy = 3;
		panel1.add(info04, gbc);
		
		cancelButton = new JButton("Schließen");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				modelDialog.setVisible(false);
			}
		});

		gbc.gridy = 4;
		gbc.gridx = 1;
		panel1.add(cancelButton, gbc);
		dialogContainer.add(panel1);
	}

}
