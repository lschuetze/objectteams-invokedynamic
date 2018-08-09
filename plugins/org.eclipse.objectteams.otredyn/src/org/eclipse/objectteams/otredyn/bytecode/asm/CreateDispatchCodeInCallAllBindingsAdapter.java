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

import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Create the code for the dispatch from a base class to the teams 
 * in the method callAllBindings. <br/> <br/>
 * The code was generated as follows: <br/>
 * <code>
 * switch (boundMethodId) { // this was generated in CreateSwitchAdapter <br/>
 * ... <br/>
 * case (boundMethodId): <br/>
 *     Teams[] teams = TeamManager.getTeams(joinpointId) <br/>
 *     if (teams == null) { <br/>
 *         break; <br/>
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
public class CreateDispatchCodeInCallAllBindingsAdapter extends
		AbstractCreateDispatchCodeAdapter {

	private int joinpointId;
	private int boundMethodId;
	
	public CreateDispatchCodeInCallAllBindingsAdapter(int joinpointId,
			int boundMethodId) {
		super(false, 3); // 'this' plus two args (int, Object[])
		this.joinpointId = joinpointId;
		this.boundMethodId = boundMethodId;
	}

	@Override
	public boolean transform() {
		MethodNode callAllBindings = getMethod(ConstantMembers.callAllBindingsClient);
		InsnList instructions = getDispatchCode(callAllBindings, joinpointId, -1); // dynamically use iload_0 instead of constant
		addNewLabelToSwitch(callAllBindings.instructions, instructions, boundMethodId);
		addLocals(callAllBindings);
		callAllBindings.maxStack = getMaxStack();
		callAllBindings.maxLocals = getMaxLocals();
		return true;
	}

	@Override
	protected InsnList getBoxedArguments(Type[] args) {
		InsnList instructions = new InsnList();
		instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
		return instructions;
	}

}
