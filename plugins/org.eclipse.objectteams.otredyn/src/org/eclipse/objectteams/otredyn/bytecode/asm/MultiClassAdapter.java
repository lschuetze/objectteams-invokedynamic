/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2012 Oliver Frank and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 *		Oliver Frank - Initial API and implementation
 *		Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otredyn.bytecode.asm;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.eclipse.objectteams.otredyn.bytecode.asm.AsmBoundClass.ASM_API;

/**
 * This class is needed to make it possible, that many 
 * ClassAdpters can manipulate the bytecode in one run.
 * It just delegates all method calls to the concrete Adapters.
 * @author Oliver Frank
 */
class MultiClassAdapter extends ClassVisitor {

	private List<ClassVisitor> visitors;
	private ClassVisitor toplevelVisitor;
	
	public MultiClassAdapter(ClassVisitor v) {
		super(ASM_API, v);
		this.visitors = new ArrayList<ClassVisitor>();
	}

	public void addVisitor(ClassVisitor v) {
		visitors.add(v);
	}
	
	/** We only support one visitor to intercept the toplevel class definition. */
	public void setToplevelVisitor(ClassVisitor v) {
		this.toplevelVisitor = v;
	}
	
	public boolean hasVisitors() {
		return this.toplevelVisitor != null || !this.visitors.isEmpty();
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		if (this.toplevelVisitor != null)
			this.toplevelVisitor.visit(version, access, name, signature, superName, interfaces);
		else
			super.visit(version, access, name, signature, superName, interfaces);
	}
	@Override
	public void visitEnd() {
		for (ClassVisitor visitor : visitors) {
			visitor.visitEnd();
		}
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		MethodVisitor result = null;
		for (ClassVisitor visitor : visitors) {
			result = visitor.visitMethod(access, name, desc, signature, exceptions);
			if (result != null) {
				return result;
			}
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}
}
