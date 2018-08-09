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
import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Creates and adds the instructions,
 * that are needed to access a not visible method from the team
 * in the method access or accessStatic as follows:<br/> <br/>
 * case (memberId) { // generated by CreateSwitchForAccessAdapter <br/>
 *     return orgMethod(args[0], ..., args[args.length]); <br/>
 * }
 * 
 * @author Oliver Frank
 */
public class CreateMethodAccessAdapter extends AbstractTransformableClassNode {
	private Method method;
	private int accessId;
	private Method access;
	private int firstArgIndex;
	private boolean isConstructor;

	public CreateMethodAccessAdapter(Method method, int accessId) {
		this.method = method;
		this.accessId = accessId;
		isConstructor = method.getName().equals("<init>");
		if (method.isStatic() || isConstructor) {
			access = ConstantMembers.accessStatic;
			firstArgIndex = 0;
		} else {
			access = ConstantMembers.access;
			firstArgIndex = 1;
		}
	}
	
	@Override
	public boolean transform() {
		String desc = method.getSignature();
		InsnList instructions = new InsnList();
		
		if (isConstructor) {
			// create empty object for constructor invocation:
			instructions.add(new TypeInsnNode(Opcodes.NEW, name));
			instructions.add(new InsnNode(Opcodes.DUP));
		} else if (!method.isStatic()) {
			//put "this" on the stack for a non-static method
			instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		}
		
		//Unbox arguments
		Type[] args = Type.getArgumentTypes(desc);
		
		if (args.length > 0) {
			
			
			for (int i = 0; i < args.length; i++) {
				instructions.add(new VarInsnNode(Opcodes.ALOAD, firstArgIndex + 2));
				instructions.add(createLoadIntConstant(i));
				instructions.add(new InsnNode(Opcodes.AALOAD));
				Type arg = args[i];
				if (arg.getSort() != Type.ARRAY && arg.getSort() != Type.OBJECT) {
					String objectType = AsmTypeHelper.getBoxingType(arg);
					instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, objectType));
					instructions.add(AsmTypeHelper.getUnboxingInstructionForType(arg, objectType));
				} else {
					instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, arg.getInternalName()));
				}
			}
		}
		
		//call original method
		int opcode = Opcodes.INVOKEVIRTUAL;
		if (method.isStatic()) {
			opcode = Opcodes.INVOKESTATIC;
		} else if (isConstructor) {
			opcode = Opcodes.INVOKESPECIAL;
		}
		instructions.add(new MethodInsnNode(opcode, name, method.getName(), method.getSignature(), false));
		
		
		//box return value
		Type returnType = Type.getReturnType(desc);

		if (returnType.getSort() != Type.OBJECT &&
				returnType.getSort() != Type.ARRAY &&
				returnType.getSort() != Type.VOID) {
			
				instructions.add(AsmTypeHelper.getBoxingInstructionForType(returnType));
				instructions.add(new InsnNode(Opcodes.ARETURN));
		} else if (returnType.getSort() == Type.VOID && !isConstructor) {
			instructions.add(new InsnNode(Opcodes.ACONST_NULL));
			instructions.add(new InsnNode(Opcodes.ARETURN));
		} else {
			instructions.add(new InsnNode(Opcodes.ARETURN));
		}
		
		//add the instructions to a new label in the existing switch
		MethodNode access = getMethod(this.access);
		addNewLabelToSwitch(access.instructions, instructions, accessId);
		
		return true;
	}

}
