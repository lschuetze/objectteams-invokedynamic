/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2015 Oliver Frank and others.
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
import org.objectweb.asm.Type;

import static org.eclipse.objectteams.otredyn.bytecode.asm.AsmBoundClass.ASM_API;

/**
 * This class adds an method only with a return statement
 * to the bytecode of a class with the ASM Core API  
 * @author Oliver Frank
 */
class AddEmptyMethodAdapter extends ClassVisitor {

	private int access;
	private String name;
	private String desc;
	private String signature;
	private String[] exceptions;
	private int maxLocals;
	private String superToCall;

	public AddEmptyMethodAdapter(ClassVisitor cv, String name, int access,
			String desc, String[] exceptions, String signature,
			int maxLocals, String superToCall) {
		super(ASM_API, cv);
		this.access = access;
		this.desc = desc;
		this.exceptions = exceptions;
		this.name = name;
		this.signature = signature;
		this.maxLocals = maxLocals;
		this.superToCall = superToCall;
	}

	@Override
	public void visitEnd() {
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		if ((this.access & Opcodes.ACC_ABSTRACT) != 0) {
			mv.visitEnd();
			return;
		}
		mv.visitCode();
		boolean needConstValue = true;
		if (superToCall != null) {
			needConstValue = false;
			boolean isStatic = (this.access & Opcodes.ACC_STATIC) != 0;
			int firstArgIndex = isStatic ? 0 : 1;
			if (!isStatic)
				mv.visitVarInsn(Opcodes.ALOAD, 0); // "this"
			Type[] args = Type.getArgumentTypes(desc);
			for (int i=0, slot=firstArgIndex; i < args.length; slot+=args[i++].getSize())
				mv.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superToCall, name, desc, false);
		}
		Type returnType = Type.getReturnType(this.desc);
		switch (returnType.getSort()) {
		case Type.VOID:
			mv.visitInsn(Opcodes.RETURN);
			break;
		case Type.INT:
        case Type.BOOLEAN:
        case Type.CHAR:
        case Type.BYTE:
        case Type.SHORT:
        	if (needConstValue)
        		mv.visitInsn(Opcodes.ICONST_1);
			mv.visitInsn(Opcodes.IRETURN);
			break;
        case Type.FLOAT:
        	if (needConstValue)
        		mv.visitInsn(Opcodes.FCONST_1);
			mv.visitInsn(Opcodes.FRETURN);
			break;
        case Type.LONG:
        	if (needConstValue)
        		mv.visitInsn(Opcodes.LCONST_1);
			mv.visitInsn(Opcodes.LRETURN);
			break;
        case Type.DOUBLE:
		case Type.OBJECT:
		case Type.ARRAY:
			if (needConstValue)
        		mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			break;
		}
		mv.visitMaxs(1, maxLocals);
		mv.visitEnd();
	}
	
	@Override
	public MethodVisitor visitMethod(int arg0, String arg1, String arg2, String arg3, String[] arg4) {
		return null; // also consider other visitors
	}

	@Override
	public String toString() {
		return "AddEmptyMethod "+this.name+this.desc+" call super: "+this.superToCall;
	}
}