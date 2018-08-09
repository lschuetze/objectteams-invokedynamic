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

import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Initially prepares the method callAllBindings as follows: <br/><br/>
 * <code>
 * switch (boundMethodId) {<br/>
 * default:<br/>
 *     break;<br/> 
 * }<br/>
 * <br/>
 * return callOrig(boundMethodId, args);<br/>
 * </code>
 * @author Oliver Frank
 */
public class CreateSwitchForCallAllBindingsNode extends CreateSwitchAdapter {
	private Type[] args;
	private LabelNode gotoLabel;
	
	public CreateSwitchForCallAllBindingsNode() {
		super(ConstantMembers.callAllBindingsClient);
	}
	
	@Override
	protected void addInstructionForDefaultLabel(MethodNode method) {
		gotoLabel = new LabelNode();
		method.instructions.add(new JumpInsnNode(Opcodes.GOTO, gotoLabel));
	}
	
	@Override
	protected void addPostSwitchInstructions(MethodNode method) {
		method.instructions.add(gotoLabel);
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		
		args = Type.getArgumentTypes(method.desc);
		int length = args.length;
		for (int i = 0; i < length; i++) {
			Type arg = args[i]; 
			method.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), i + 1));
		}
		
		// return callOrig(boundMethodId, args);
		method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, name, ConstantMembers.callOrig.getName(), ConstantMembers.callOrig.getSignature(), false));
		method.instructions.add(new InsnNode(Opcodes.ARETURN));
	}
	
	@Override
	protected int getMaxStack() {
		return args.length + 1;
	}

}
