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

import org.eclipse.objectteams.otredyn.transformer.names.ClassNames;
import org.eclipse.objectteams.otredyn.transformer.names.ConstantMembers;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Create the code for the dispatch from a base class to the teams. <br/> <br/>
 * The code was generated as follows: <br/>
 * <code>
 * switch (boundMethodId) { // this was generated in CreateSwitchAdapter <br/>
 * ... <br/>
 * case (...): // this was generated in concrete implementations <br/> 
 *             // of this abstract class <br/>
 *     Teams[] teams = TeamManager.getTeams(joinpointId) <br/><br/>
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
public abstract class AbstractCreateDispatchCodeAdapter extends
		AbstractTransformableClassNode {

	final int teamsAndCallinsSlot;

	private boolean isStatic;

	public AbstractCreateDispatchCodeAdapter(boolean isStatic, int locals) {
		this.isStatic = isStatic;
		this.teamsAndCallinsSlot = locals;
	}

	private Type[] args;

	protected InsnList getDispatchCode(MethodNode method, int joinPointId,
			int boundMethodId) {
		InsnList instructions = new InsnList();

		// teamsAndCallinIds = TeamManager.getTeamsAndCallinIds(joinpointId);
		instructions.add(createLoadIntConstant(joinPointId));

		instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
				ClassNames.TEAM_MANAGER_SLASH, ConstantMembers.getTeamsAndCallinIds.getName(),
				ConstantMembers.getTeamsAndCallinIds.getSignature(),
				false));
		instructions.add(createInstructionsToCheckTeams(method)); // skip the rest if null
		instructions.add(new IntInsnNode(Opcodes.ASTORE, teamsAndCallinsSlot));

		// teams = teamsAndCallinIds[0]
		instructions.add(new VarInsnNode(Opcodes.ALOAD, teamsAndCallinsSlot));
		instructions.add(new InsnNode(Opcodes.ICONST_0));
		instructions.add(new InsnNode(Opcodes.AALOAD));
		instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Lorg/objectteams/ITeam;"));
		
		
		// get the first team
		instructions.add(new InsnNode(Opcodes.DUP));
		instructions.add(new InsnNode(Opcodes.ICONST_0));
		instructions.add(new InsnNode(Opcodes.AALOAD));
		instructions.add(new InsnNode(Opcodes.SWAP));
		if (isStatic) {
			instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		} else {
			// put "this" on the stack and cast it to IBoundBase2
			instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
					ClassNames.I_BOUND_BASE_SLASH));
		}
		instructions.add(new InsnNode(Opcodes.SWAP));
		// start index
		instructions.add(new InsnNode(Opcodes.ICONST_0));

		// callinIds = teamsAndCallinIds[1]
		instructions.add(new VarInsnNode(Opcodes.ALOAD, teamsAndCallinsSlot));
		instructions.add(new InsnNode(Opcodes.ICONST_1));
		instructions.add(new InsnNode(Opcodes.AALOAD));
		instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "[I"));

		if (boundMethodId == -1)
			instructions.add(new VarInsnNode(Opcodes.ILOAD, 1)); // boundMethodId is arg#1 inside _OT$callAllBindings (base version)
		else
			instructions.add(createLoadIntConstant(boundMethodId));
		args = Type.getArgumentTypes(method.desc);

		// box the arguments
		instructions.add(getBoxedArguments(args));

		instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
						ClassNames.ITEAM_SLASH, 
						ConstantMembers.callAllBindingsTeam.getName(),
						ConstantMembers.callAllBindingsTeam.getSignature(),
						true));

		Type returnType = Type.getReturnType(method.desc);
		instructions.add(getUnboxingInstructionsForReturnValue(returnType));

		return instructions;
	}

	protected void addLocals(MethodNode method) {
		String selector = "_OT$teamsAndCallinIds";
		for (Object lv : method.localVariables) {
			if (((LocalVariableNode)lv).name.equals(selector))
				return;
		}
		method.visitLocalVariable(selector, "[Ljava/lang/Object;", null, new Label(), new Label(), teamsAndCallinsSlot);
	}
	
	protected InsnList createInstructionsToCheckTeams(MethodNode method) {
		// if (teams == null) {
		// 		break;
		// }
		InsnList instructions = new InsnList();
		instructions.add(new InsnNode(Opcodes.DUP));
		LabelNode label = new LabelNode();
		instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, label));
		instructions.add(new InsnNode(Opcodes.POP));
		instructions.add(new JumpInsnNode(Opcodes.GOTO, findBreakLabel(method.instructions)));
		instructions.add(label);
		return instructions;
	}

	private LabelNode findBreakLabel(InsnList instructions) {
		for (int i = instructions.size() - 2; i >= 0; i--) { // skip terminating label (for local variable range)
			AbstractInsnNode node = instructions.get(i);
			if (node.getType() == AbstractInsnNode.LABEL) {
				return (LabelNode) node;
			}
		}
		throw new RuntimeException("Can't find break label to create dispatch code");
	}

	protected abstract InsnList getBoxedArguments(Type[] args);

	protected int getMaxLocals() {
		return teamsAndCallinsSlot+1;
	}

	protected int getMaxStack() {
		return 10;
	}
}
