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

import org.eclipse.objectteams.otredyn.bytecode.Method;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * This class creates an empty switch statement in a method.
 * @author Oliver Frank
 */
public class CreateSwitchAdapter extends AbstractTransformableClassNode {
	private Method method;

	private int firstArgIndex;

	private String superToCall;

	public CreateSwitchAdapter(Method method) {
		this.method = method;
		if (method.isStatic()) {
			firstArgIndex = 0;
		} else {
			firstArgIndex = 1;
		}
	}

	public CreateSwitchAdapter(Method method, String superToCall) {
		this.method = method;
		if (method.isStatic()) {
			firstArgIndex = 0;
		} else {
			firstArgIndex = 1;
		}
		this.superToCall = superToCall;
	}

	public CreateSwitchAdapter(Method method, boolean isRoleMethod) {
		this.method = method;
		if (method.isStatic()) {
			if (isRoleMethod)
				firstArgIndex = 2;
			else
				firstArgIndex = 0;
		} else {
			firstArgIndex = 1;
		}
	}
	
	@Override
	public boolean transform() {
		MethodNode methodNode = getMethod(method);
		if (methodNode == null) return false; // doesn't exist, don't transform
		methodNode.instructions.clear();
		
		addLineNumber(methodNode.instructions, 1);
		addPreSwitchInstructions(methodNode);
		
		LabelNode def = new LabelNode();
		LookupSwitchInsnNode switchNode = new LookupSwitchInsnNode(def, new int[0], new LabelNode[0]);
		
		methodNode.instructions.add(switchNode);
		methodNode.instructions.add(def);
		addInstructionForDefaultLabel(methodNode);
		
		addPostSwitchInstructions(methodNode);

		if ((methodNode.access & Opcodes.ACC_STATIC) == 0)
			addThisVariable(methodNode);

		methodNode.maxStack = getMaxStack();
		return true;
	}
	
	/**
	 * Adds instructions after the switch statement
	 * @param method
	 */
	protected void addPostSwitchInstructions(MethodNode method) {
	}

	/**
	 * Adds instructions before the switch statement.
	 * @param method
	 */
	protected void addPreSwitchInstructions(MethodNode method) {
		method.instructions.add(new VarInsnNode(Opcodes.ILOAD, firstArgIndex));
		method.instructions.add(createLoadIntConstant(0x7fffffff));
		method.instructions.add(new InsnNode(Opcodes.IAND)); // mask the 0x80000000 bit used for signaling a ctor
	}
	
	/**
	 * Adds instructions for the default label.
	 * @param method
	 */
	protected void addInstructionForDefaultLabel(MethodNode method) {
		if (superToCall != null) {
			method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
			method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, superToCall, this.method.getName(), this.method.getSignature(), false));
		} else {
			method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		}
		method.instructions.add(new InsnNode(Opcodes.ARETURN));
	}
	
	protected int getMaxStack() {
		return 1;
	}
	
	protected int getFirstArgIndex() {
		return firstArgIndex;
	}
	
	protected Method getMethod() {
		return method;
	}

}
