/**
 * Copyright (C) 2010-2014 Fabric project group, Cornell University
 *
 * This file is part of Fabric.
 *
 * Fabric is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * Fabric is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 */
package sif.html;

import sif.servlet.Action;
import sif.servlet.HTMLWriter;
import fabric.lang.security.Label;
import fabric.lang.security.Principal;
import fabric.lang.security.PrincipalUtil;

/** A Form contains Inputs and generates requests. */
public final class Form extends BlockContainer {
    private final Action action;
    private final Principal servletP;
    
    public Form(Principal servletP, Label L, Label E, Action action_, Label cL, Label cE, Node n) {
        super(L, E, "form", cL, cE, n);
        this.servletP = servletP;
        action = action_;
    }
    void writeOptions(HTMLWriter p) {
        p.print(" method=POST");
        p.print(" enctype=\"multipart/form-data\"");
        //p.breakLine();
        p.print(" action=");
        p.print("\"");
        p.printServletURL();
        p.print("\"");
        
        p.addAction(action, this);
        p.print(" name=\"");
        p.printActionName(action, this);
        p.print(" \"");
    }
    
    void writeContents(HTMLWriter p) {
	// XXX Should check that the form contents doesn't contain any input
	// nodes intended for off-site forms.

        // p.breakLine();
        if (contents != null)
            contents.write(p, this);
        p.breakLine();
        p.print("<input");
        p.print(" type=\"hidden\"");
        p.print(" name=\"action\" value=\"");
        p.printActionName(this.action, this);
        // p.allowBreak(0, 2, " ");
        p.print("\"/>");
    }
    boolean isBigContainer() {
        return true;
    }
    
    public static boolean jif$Instanceof(Principal P, Label l, Label e, Object o) {
	if ((o instanceof Form) && Node.jif$Instanceof(l, e, o)) {
	    Form that = (Form)o;
	    return PrincipalUtil._Impl.equivalentTo(that.servletP, P);
	}
    return false;
    }

    public static Form jif$cast$sif_html_Form(Principal P, Label l, Label e, Object o) {
        if (o == null) return null; 
	if (jif$Instanceof(P, l, e, o))
	    return (Form)o;
	throw new ClassCastException();
    }
}
