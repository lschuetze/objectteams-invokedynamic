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

import org.eclipse.objectteams.otredyn.bytecode.AbstractBoundClass;
import org.eclipse.objectteams.otredyn.bytecode.Method;
import org.eclipse.objectteams.otredyn.transformer.names.ClassNames;
import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Initially prepares the method access or accessStatic as follows: <br/><br/>
 * <code>
 * int memberId = TeamManager.getMemberId(accessId, caller);<br/>
 * switch (memberId) {<br/>
 * default:<br/>
 *     return super.access(accessId, opKind, args, caller);<br/> 
 * }<br/>
 * </code>
 * @author Oliver Frank
 */
public class CreateSwitchForAccessAdapter extends CreateSwitchAdapter {

	private String superClassName;
	private AbstractBoundClass clazz;
	public CreateSwitchForAccessAdapter(Method method, String superClassName, AbstractBoundClass clazz) {
		super(method);
		this.superClassName = superClassName;
		this.clazz = clazz;
	}

	@Override
	protected void addPreSwitchInstructions(MethodNode method) {
		// put "accessId" on the stack
		method.instructions.add(new VarInsnNode(Opcodes.ILOAD, getFirstArgIndex()));
		// put "caller".getClass() on the stack
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, getFirstArgIndex() + 3));
		method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
		// call "getMemberId(accessId, callerClass)
		method.instructions
				.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
						ClassNames.TEAM_MANAGER_SLASH,
						ConstantMembers.getMemberId.getName(),
						ConstantMembers.getMemberId.getSignature(),
						false));
	}
	
	@Override
	protected void addInstructionForDefaultLabel(MethodNode method) {
		if (superClassName == null || superClassName.equals("java/lang/Object")) {
			method.instructions.add(new TypeInsnNode(Opcodes.NEW, "org/objectteams/NoSuchMethodError"));
			method.instructions.add(new InsnNode(Opcodes.DUP));
			method.instructions.add(new VarInsnNode(Opcodes.ILOAD, getFirstArgIndex())); // accessId
			method.instructions.add(new LdcInsnNode(clazz.getName()));					 // current class
			method.instructions.add(new LdcInsnNode("decapsulating access"));			 // access reason
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "org/objectteams/NoSuchMethodError", "<init>", "(ILjava/lang/String;Ljava/lang/String;)V", false));
			method.instructions.add(new InsnNode(Opcodes.ATHROW));
		} else {
			Type[] args = Type.getArgumentTypes(method.desc);
			addInstructionsForLoadArguments(method.instructions, args, getMethod().isStatic());
	
			int opcode = Opcodes.INVOKESPECIAL;
			if (getMethod().isStatic()) {
				opcode = Opcodes.INVOKESTATIC;
			}
			method.instructions.add(new MethodInsnNode(opcode,
					superClassName, getMethod().getName(),
					getMethod().getSignature(), false));
			method.instructions.add(new InsnNode(Opcodes.ARETURN));
		}
	}
	
	@Override
	protected int getMaxStack() {
		return 6;
	}

}
