//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Andrew Hinton <ug60axh@cs.bham.uc.uk> (University of Birmingham)
//	* Dave Parker <dxp@cs.bham.uc.uk> (University of Birmingham)
//	
//------------------------------------------------------------------------------
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

package userinterface.util;

import parser.*;
import java.io.ByteArrayInputStream;
import javax.swing.table.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.event.*;
import prism.*;

/**
 *  This does not store an expression, it actually maintains a string which is
 *  tested for its validity with the PRISM parser
 * @author  Andrew Hinton
 */
public class ExpressionProperty extends SingleProperty
{
    
    protected Prism pr;
    
    private boolean valid;
    /** Creates a new instance of ExpressionProperty */
    public ExpressionProperty(PropertyOwner owner,String name, String value, Prism pr)
    {
        this(owner, name, value, "", pr);
    }
    
    public ExpressionProperty(PropertyOwner owner,String name, String value, String comment, Prism pr)
    {
        super(owner,name, "", "", true, comment);
        this.pr = pr;
        valid = true;
        area = new JTextArea();
        area.setLineWrap(false);
        area.setOpaque(true);
        try
        {
            setProperty(value);
        }
        catch(Exception e)
        {
        }
    }
    
    public String getExpression()
    {
        return (String)getProperty();
    }
    
    public void setProperty(Object property) throws PropertyException
    {
        
        String expression = property.toString();
        try
        {
            if(pr == null) throw new ParseException();
            if(!expression.equals(""))
            {
                Expression e = pr.parseSingleExpressionString(expression);
            }
            
            
            valid = true;
            
            
           
        }
        catch(ParseException e)
        {
            valid = false;
            //System.out.println("Error: "+e);
        }
        catch(PrismException e)
        {
            valid = false;
            //System.out.println("Error: "+e);
        }
        
        super.setProperty(property);
    }	

	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);

		if(area!=null)area.setEnabled(enabled);
	}
    
    JTextArea area;
    
    
    public Component getTableCellRendererComponent(JTable table, Object value,
    boolean isSelected, boolean hasFocus, int row, int column)
    {
        ////System.out.println("rendering "+value.toString());
        if (isSelected)
        {
            area.setForeground(table.getSelectionForeground());
            area.setBackground(table.getSelectionBackground());
        }
        else
        {
            area.setForeground(table.getForeground());
            area.setBackground(table.getBackground());
        }
        
        
            area.setFont(table.getFont());
            
            if (hasFocus)
            {
                area.setBorder( UIManager.getBorder("Table.focusCellHighlightBorder") );
                if (table.isCellEditable(row, column))
                {
                    area.setForeground( UIManager.getColor("Table.focusCellForeground") );
                    area.setBackground( UIManager.getColor("Table.focusCellBackground") );
                }
            } else
            {
                area.setBorder(new EmptyBorder(0, 2, 2, 1));
            }
            //area.setBackground( Color.yellow);
            area.setMargin(new Insets(0, 2, 4, 2));
            area.setText((value == null) ? "" : toString());
            
            if(!valid) 
            {
                area.setForeground( Color.red);
                
            }
            return area;
        
    }
    
    
    
}
