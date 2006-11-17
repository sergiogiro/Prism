//==============================================================================
//
//	Copyright (c) 2006, Mark Kattenbelt
//
//	This file is part of PRISM.
//
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//==============================================================================

package userinterface.simulator;

import parser.*;
import prism.*;

import javax.swing.*;
import java.awt.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.util.*;
import java.awt.event.*;
import userinterface.*;
import simulator.*;

public class GUIViewDialog extends JDialog implements KeyListener
{         
	//ATTRIBUTES    
	private boolean cancelled = true;
	private boolean askOption;
    	
	private Action okAction;
	private Action cancelAction;
		
	private GUIPrism gui;
	private GUISimulator.PathTableModel pathTableModel;
    
	private ModulesFile mf;
	private SimulatorEngine engine;
	
	private VariableListModel visibleListModel;
	private VariableListModel hiddenListModel;
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel allPanel;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel centerColumn;
    private javax.swing.JPanel centerPanel;
    private javax.swing.JLabel hiddenLabel;
    private javax.swing.JList hiddenList;
    private javax.swing.JScrollPane hiddenScrollList;
    private javax.swing.JPanel leftColumn;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JButton makeHiddenButton;
    private javax.swing.JButton makeVisibleButton;
    private javax.swing.JButton okayButton;
    private javax.swing.JCheckBox optionCheckBox;
    private javax.swing.JPanel rightColumn;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JButton selectAllHidden;
    private javax.swing.JButton selectAllVisibleButton;
    private javax.swing.JTabbedPane tabPane;
    private javax.swing.JPanel variablePanel;
    private javax.swing.JLabel visibleLabel;
    private javax.swing.JList visibleList;
    private javax.swing.JScrollPane visibleScrollList;
    // End of variables declaration//GEN-END:variables
    
	/** Creates new form GUIConstantsPicker */
	public GUIViewDialog(GUIPrism parent, GUISimulator.PathTableModel pathTableModel, ModulesFile mf)
	{
		super(parent, "Configure View for Simulation", true);
        
		this.gui = parent;
		this.mf = mf;      
		this.pathTableModel = pathTableModel;
		this.engine = pathTableModel.getEngine();
		
		//initialise
		initComponents();
		
		this.tabPane.add("Variable Visibility", variablePanel);
		
		this.getRootPane().setDefaultButton(okayButton);
						
		super.setBounds(new Rectangle(550, 300));
		setResizable(true);
		setLocationRelativeTo(getParent()); // centre
		
		this.askOption = ((GUIPrism)this.getParent()).getPrism().getSettings().getBoolean(PrismSettings.SIMULATOR_NEW_PATH_ASK_VIEW);
		optionCheckBox.setSelected(this.askOption);
		
		visibleListModel = new VariableListModel(pathTableModel.getVisibleVariables());
		hiddenListModel = new VariableListModel(pathTableModel.getHiddenVariables());
		
		visibleList.setModel(visibleListModel);
		hiddenList.setModel(hiddenListModel);	
		
		this.setVisible(true);
	}
    
	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        visibleLabel = new javax.swing.JLabel();
        hiddenLabel = new javax.swing.JLabel();
        allPanel = new javax.swing.JPanel();
        bottomPanel = new javax.swing.JPanel();
        buttonPanel = new javax.swing.JPanel();
        okayButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        optionCheckBox = new javax.swing.JCheckBox();
        tabPane = new javax.swing.JTabbedPane();
        variablePanel = new javax.swing.JPanel();
        leftColumn = new javax.swing.JPanel();
        leftPanel = new javax.swing.JPanel();
        visibleScrollList = new javax.swing.JScrollPane();
        visibleList = new javax.swing.JList();
        selectAllVisibleButton = new javax.swing.JButton();
        centerColumn = new javax.swing.JPanel();
        centerPanel = new javax.swing.JPanel();
        makeVisibleButton = new javax.swing.JButton();
        makeHiddenButton = new javax.swing.JButton();
        rightColumn = new javax.swing.JPanel();
        rightPanel = new javax.swing.JPanel();
        hiddenScrollList = new javax.swing.JScrollPane();
        hiddenList = new javax.swing.JList();
        selectAllHidden = new javax.swing.JButton();

        visibleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        visibleLabel.setText("Visible Variables");
        hiddenLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hiddenLabel.setText("Hidden Variables");

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        allPanel.setLayout(new java.awt.BorderLayout());

        allPanel.setBorder(new javax.swing.border.EmptyBorder(new java.awt.Insets(5, 5, 5, 5)));
        bottomPanel.setLayout(new java.awt.BorderLayout());

        buttonPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        okayButton.setText("Okay");
        okayButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okayButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(okayButton);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        buttonPanel.add(cancelButton);

        bottomPanel.add(buttonPanel, java.awt.BorderLayout.EAST);

        optionCheckBox.setLabel("Always prompt for view configuration on path creation");
        bottomPanel.add(optionCheckBox, java.awt.BorderLayout.WEST);
        optionCheckBox.getAccessibleContext().setAccessibleName("optionCheckBox");

        allPanel.add(bottomPanel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(allPanel, java.awt.BorderLayout.SOUTH);

        tabPane.setBorder(new javax.swing.border.EmptyBorder(new java.awt.Insets(5, 5, 5, 5)));
        variablePanel.setLayout(new java.awt.GridBagLayout());

        variablePanel.setBorder(new javax.swing.border.EmptyBorder(new java.awt.Insets(5, 5, 5, 5)));
        leftColumn.setLayout(new java.awt.BorderLayout());

        leftColumn.setBorder(new javax.swing.border.TitledBorder("Visible Variables"));
        leftPanel.setLayout(new java.awt.BorderLayout(0, 5));

        leftPanel.setBorder(new javax.swing.border.EmptyBorder(new java.awt.Insets(5, 5, 5, 5)));
        visibleScrollList.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        visibleScrollList.setViewportView(visibleList);

        leftPanel.add(visibleScrollList, java.awt.BorderLayout.CENTER);

        selectAllVisibleButton.setText("Select All");
        selectAllVisibleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllVisibleButtonActionPerformed(evt);
            }
        });

        leftPanel.add(selectAllVisibleButton, java.awt.BorderLayout.SOUTH);

        leftColumn.add(leftPanel, java.awt.BorderLayout.CENTER);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        variablePanel.add(leftColumn, gridBagConstraints);

        centerColumn.setLayout(new java.awt.BorderLayout());

        centerPanel.setLayout(new java.awt.GridBagLayout());

        makeVisibleButton.setText("<<");
        makeVisibleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                makeVisibleButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        centerPanel.add(makeVisibleButton, gridBagConstraints);

        makeHiddenButton.setText(">>");
        makeHiddenButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                makeHiddenButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        centerPanel.add(makeHiddenButton, gridBagConstraints);

        centerColumn.add(centerPanel, java.awt.BorderLayout.CENTER);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        variablePanel.add(centerColumn, gridBagConstraints);

        rightColumn.setLayout(new java.awt.BorderLayout());

        rightColumn.setBorder(new javax.swing.border.TitledBorder("Hidden Variables"));
        rightPanel.setLayout(new java.awt.BorderLayout(0, 5));

        rightPanel.setBorder(new javax.swing.border.EmptyBorder(new java.awt.Insets(5, 5, 5, 5)));
        hiddenScrollList.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        hiddenScrollList.setViewportView(hiddenList);

        rightPanel.add(hiddenScrollList, java.awt.BorderLayout.CENTER);

        selectAllHidden.setText("Select All");
        selectAllHidden.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllHiddenActionPerformed(evt);
            }
        });

        rightPanel.add(selectAllHidden, java.awt.BorderLayout.SOUTH);

        rightColumn.add(rightPanel, java.awt.BorderLayout.CENTER);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        variablePanel.add(rightColumn, gridBagConstraints);

        tabPane.addTab("Variable Visibility", variablePanel);

        getContentPane().add(tabPane, java.awt.BorderLayout.CENTER);

    }
    // </editor-fold>//GEN-END:initComponents

    private void selectAllVisibleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllVisibleButtonActionPerformed
// TODO add your handling code here:
    	
    	int[] indices = new int[visibleListModel.getSize()];
    	for (int i = 0; i < indices.length; i++)
    		indices[i] = i;
    	
    	visibleList.setSelectedIndices(indices);    	
    }//GEN-LAST:event_selectAllVisibleButtonActionPerformed

    private void selectAllHiddenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllHiddenActionPerformed
// TODO add your handling code here:
    	int[] indices = new int[hiddenListModel.getSize()];
    	for (int i = 0; i < indices.length; i++)
    		indices[i] = i;
    	
    	hiddenList.setSelectedIndices(indices);
    	
    }//GEN-LAST:event_selectAllHiddenActionPerformed

    private void makeVisibleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_makeVisibleButtonActionPerformed
// TODO add your handling code here:
    	int[] indices = hiddenList.getSelectedIndices();
    	
    	for (int i = indices.length - 1; i >= 0; i--)
    	{
    		GUISimulator.Variable var = (GUISimulator.Variable)hiddenListModel.get(indices[i]);
    		
    		hiddenListModel.removeVariable(var);
    		visibleListModel.addVariable(var);
    	}   	
    }//GEN-LAST:event_makeVisibleButtonActionPerformed

    private void makeHiddenButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_makeHiddenButtonActionPerformed
// TODO add your handling code here:
    	int[] indices = visibleList.getSelectedIndices();
    	
    	for (int i = indices.length - 1; i >= 0; i--)
    	{
    		GUISimulator.Variable var = (GUISimulator.Variable)visibleListModel.get(indices[i]);
    		
    		visibleListModel.removeVariable(var);
    		hiddenListModel.addVariable(var);
    	}
    }//GEN-LAST:event_makeHiddenButtonActionPerformed
                    
	
    
	public static double log(double base, double x)
	{
		return Math.log(x) / Math.log(base);
	} 
	
	    
	private void okayButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_okayButtonActionPerformed
	{//GEN-HEADEREND:event_okayButtonActionPerformed
		
		if (optionCheckBox.isSelected() != this.askOption)
		{
			this.askOption = !this.askOption;
				
			try
			{
				((GUIPrism)this.getParent()).getPrism().getSettings().set(PrismSettings.SIMULATOR_NEW_PATH_ASK_VIEW, this.askOption);
			}
			catch (PrismException e) {}
		}	
			
		pathTableModel.setVisibility(visibleListModel.getVariables(), hiddenListModel.getVariables());		    
		dispose();
	}//GEN-LAST:event_okayButtonActionPerformed
        
	private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
	{//GEN-HEADEREND:event_cancelButtonActionPerformed
		dispose();
	}//GEN-LAST:event_cancelButtonActionPerformed
        
	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

	public void keyPressed(KeyEvent e)
	{
	}	
        
	public void keyReleased(KeyEvent e)
	{
		
	}
	
	public void keyTyped(KeyEvent e)
	{
	    
	}
	
	
	class VariableListModel extends DefaultListModel
	{	
		public VariableListModel(ArrayList variables)
		{
			super();
			for (int i = 0; i < variables.size(); i++)
			{
				super.add(i, ((GUISimulator.Variable)variables.get(i)));
				
			}
		}
		
		public void removeVariable(GUISimulator.Variable variable)
		{
			for (int i = 0; i < super.getSize(); i++)
			{
				GUISimulator.Variable var = (GUISimulator.Variable)super.getElementAt(i);
				if (var.equals(variable))
				{
					super.remove(i);
				}
			}			
		}
		
		public void addVariable(GUISimulator.Variable variable)
		{
			int i = 0;
				
			while (i < super.getSize() && ((GUISimulator.Variable)super.getElementAt(i)).getIndex() < variable.getIndex())
			{
				i++;				
			}		
			
			super.add(i, variable);
		}
		
		public ArrayList getVariables()
		{
			ArrayList list = new ArrayList();
			for (int i = 0; i < super.getSize(); i++)
			{
				list.add(super.getElementAt(i));
			}
			
			return list;
		}
	}	
}

