/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2014 Oliver Frank and others.
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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.eclipse.objectteams.otredyn.bytecode.asm.AsmBoundClass.ASM_API;

/**
 * Adds an interface to the bytecode of a class
 * @author Oliver Frank
 *
 */
public class AddInterfaceAdapter extends ClassVisitor {
	private String interfaceName;
	
	public AddInterfaceAdapter(ClassVisitor cv, String interfaceName) {
		super(ASM_API, cv);
		this.interfaceName = interfaceName;
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		String[] newInterfaces = new String[interfaces.length + 1];
		System.arraycopy(interfaces, 0, newInterfaces, 0, interfaces.length);
		newInterfaces[interfaces.length] = interfaceName;
		// Assumption: when it should potentially be a bound baseclass it may have to be public, too:
		access &= ~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED);
		access |= Opcodes.ACC_PUBLIC;
		super.visit(version, access, name, signature, superName, newInterfaces);
	}
	
	@Override
	public MethodVisitor visitMethod(int arg0, String arg1, String arg2, String arg3, String[] arg4) {
		return null; // also consider other visitors
	}
}
