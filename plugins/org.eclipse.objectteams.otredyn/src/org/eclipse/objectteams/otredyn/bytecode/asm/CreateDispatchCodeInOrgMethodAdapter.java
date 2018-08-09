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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Create the code for the dispatch from a base class to the teams 
 * in the original method. <br/> <br/>
 * The code was generated as follows: <br/>
 * <code>
 *     Teams[] teams = TeamManager.getTeams(joinpointId) <br/>
 *     if (teams == null) { <br/>
 *         Object args[] = {arg1, ..., argn}; <br/>
 *         return callOrig(boundMethodId, args); <br/>
 *     } <br/>
 *     <br/>
 *     Team t = teams[0]; <br/>
 *     int[] callinIds = TeamManager.getCallinIds(joinpointId); <br/>
 *     Object[] args = {arg1, ... , argn};
 *     return team._OT$callAllBindings(this, teams, 0, callinIds, boundMethodId, args);
 * } <br/>
 * </code>
 * @author Oliver Frank
 */
public class CreateDispatchCodeInOrgMethodAdapter extends
		AbstractCreateDispatchCodeAdapter {
	private Method method;
	private int joinPointId;
	private int boundMethodId;
	
	public CreateDispatchCodeInOrgMethodAdapter(Method method, int joinPointId, int boundMethodId) {
		super(true, computeLocals(method.getSignature()));
		this.method = method;
		this.joinPointId = joinPointId;
		this.boundMethodId = boundMethodId;
	}

	static int computeLocals(String signature) {
		Type[] params = Type.getArgumentTypes(signature);
		int locals = 0;
		for (Type param : params)
			locals += param.getSize();
		return locals;
	}

	@Override
	public boolean transform() {
		MethodNode orgMethod = getMethod(method);
		if ((orgMethod.access & Opcodes.ACC_ABSTRACT) != 0) return false;
		
		orgMethod.instructions.clear();
		orgMethod.instructions.add(getDispatchCode(orgMethod, joinPointId, boundMethodId));
		addLocals(orgMethod);
		orgMethod.maxStack = getMaxStack();
		orgMethod.maxLocals = getMaxLocals();
		return true;
	}

	@Override
	protected InsnList getBoxedArguments(Type[] args) {
		return getBoxingInstructions(args, true);
	}

	@Override
	protected InsnList createInstructionsToCheckTeams(MethodNode method) {
		InsnList instructions = new InsnList();
		instructions.add(new InsnNode(Opcodes.DUP));
		LabelNode label = new LabelNode();
		//if (teams == null) {
		instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, label));
		instructions.add(new InsnNode(Opcodes.POP));
		//put the boundMethodId on the stack
		instructions.add(createLoadIntConstant(boundMethodId));
		Type[] args = Type.getArgumentTypes(method.desc);
		// box the arguments
		instructions.add(getBoxingInstructions(args, (method.access & Opcodes.ACC_STATIC) != 0));
		//callOrigStatic(boundMethodId, args);
		instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, name, ConstantMembers.callOrigStatic.getName(), ConstantMembers.callOrigStatic.getSignature(), false));
		Type returnType = Type.getReturnType(method.desc);
		instructions
				.add(getUnboxingInstructionsForReturnValue(returnType));
		instructions.add(label);

		return instructions;
	}
	
}
