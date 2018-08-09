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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Asm specific helper class to handle type strings used in the bytecode
 * @author Oliver Frank
 */
class AsmTypeHelper {

	public static AbstractInsnNode getUnboxingInstructionForType(Type primitiveType) {
		String objectType = getBoxingType(primitiveType);
		if (objectType == null)
			return new InsnNode(Opcodes.NOP);
		return getUnboxingInstructionForType(primitiveType, objectType);
	}

	public static AbstractInsnNode getUnboxingInstructionForType(Type primitiveType, String objectType) {
		String methodName = primitiveType.getClassName() + "Value";
		String desc = Type.getMethodDescriptor(primitiveType, new Type[] {});
		
		return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, objectType, methodName, desc, false);
	}

	public static String getBoxingType(Type primitiveType) {
		String className = null;
		switch (primitiveType.getSort()) {
		case Type.BOOLEAN:
			className = "Boolean";
			break;
		case Type.BYTE:
			className = "Byte";
			break;
		case Type.CHAR:
			className = "Character";
			break;
		case Type.DOUBLE:
			className = "Double";
			break;
		case Type.FLOAT:
			className = "Float";
			break;
		case Type.INT:
			className = "Integer";
			break;
		case Type.LONG:
			className = "Long";
			break;
		case Type.SHORT:
			className = "Short";
			break;
		default:
			return null;
		}
		className = "java/lang/" + className; 
		return className;
	}
	
	public static AbstractInsnNode getBoxingInstructionForType(Type type) {
		if (type.getSort() == Type.VOID)
			return new InsnNode(Opcodes.ACONST_NULL);

		String className = getBoxingType(type);
		if (className == null)
			return new InsnNode(Opcodes.NOP);
		
		String desc = Type.getMethodDescriptor(Type.getObjectType(className), new Type[] {type});
		return new MethodInsnNode(Opcodes.INVOKESTATIC, className, "valueOf", desc, false);
	}
}
