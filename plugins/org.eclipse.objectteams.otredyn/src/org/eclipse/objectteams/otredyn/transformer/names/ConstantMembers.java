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
package org.eclipse.objectteams.otredyn.transformer.names;

import org.eclipse.objectteams.otredyn.bytecode.Field;
import org.eclipse.objectteams.otredyn.bytecode.Method;
import org.eclipse.objectteams.otredyn.bytecode.Types;
import org.objectweb.asm.Opcodes;



/**
 * Container for methods used in the bytecode manipulating classes
 * @author Oliver Frank
 */
public abstract class ConstantMembers {
	
	// predefined field:
	public static final String OT_ROLE_SET = "_OT$roleSet";
	public static final String HASH_SET_FIELD_TYPE = Types 
			.getAsInternalType(ClassNames.HASH_SET_SLASH);
	public static final Field roleSet = new Field(OT_ROLE_SET, HASH_SET_FIELD_TYPE);
	
	// predefined methods
	public static final Method callOrig = new Method("_OT$callOrig", Types
			.getTypeStringForMethod(Types.getAsInternalType(ClassNames.OBJECT_SLASH),
					new String[] { Types.INT,
							Types.getAsArrayType(ClassNames.OBJECT_SLASH) }));

	public static final Method callOrigStatic = new Method(
			"_OT$callOrigStatic",
			Types.getTypeStringForMethod(Types
					.getAsInternalType(ClassNames.OBJECT_SLASH), new String[] {
					Types.INT, Types.getAsArrayType(ClassNames.OBJECT_SLASH) }),
			true, Opcodes.ACC_PUBLIC);

	// this method's signature actually depends on the enclosing team class
	// used when generating callOrigStatic method into a role class
	// which needs two synthetic arguments.
	public static Method callOrigStaticRoleVersion(String teamClass) {
		return  new Method(
			"_OT$callOrigStatic",
			Types.getTypeStringForMethod(
					Types.getAsInternalType(ClassNames.OBJECT_SLASH), 
					new String[] {
						Types.INT,
						Types.getAsInternalType(teamClass),
						Types.INT, 
						Types.getAsArrayType(ClassNames.OBJECT_SLASH) 
					}),
			true, Opcodes.ACC_PUBLIC);
	}

	public static final Method callAllBindingsClient = new Method(
			"callAllBindings", Types.getTypeStringForMethod(Types
					.getAsInternalType(ClassNames.OBJECT_SLASH), new String[] {
					Types.INT, Types.getAsArrayType(ClassNames.OBJECT_SLASH) }));

	public static final Method callAllBindingsTeam = new Method(
			"_OT$callAllBindings", Types.getTypeStringForMethod(Types
					.getAsInternalType(ClassNames.OBJECT_SLASH), new String[] {
					Types.getAsInternalType(ClassNames.I_BOUND_BASE_SLASH),
					Types.getAsArrayType(ClassNames.ITEAM_SLASH), Types.INT,
					Types.getAsArrayType(Types.INT), Types.INT,
					Types.getAsArrayType(ClassNames.OBJECT_SLASH) }));

	public static final Method access = new Method("_OT$access", Types
			.getTypeStringForMethod(Types.getAsInternalType(ClassNames.OBJECT_SLASH),
					new String[] { Types.INT, Types.INT,
							Types.getAsArrayType(ClassNames.OBJECT_SLASH),
							Types.getAsInternalType(ClassNames.ITEAM_SLASH) }));

	public static final Method accessStatic = new Method("_OT$accessStatic", Types
			.getTypeStringForMethod(Types.getAsInternalType(ClassNames.OBJECT_SLASH),
					new String[] { Types.INT, Types.INT,
							Types.getAsArrayType(ClassNames.OBJECT_SLASH),
							Types.getAsInternalType(ClassNames.ITEAM_SLASH) }), true, Opcodes.ACC_PUBLIC);

	public static final Method getTeamsAndCallinIds = new Method("getTeamsAndCallinIds", Types
			.getTypeStringForMethod(Types.getAsArrayType(ClassNames.OBJECT_SLASH),
					new String[] { Types.INT }));

	public static final Method getMemberId = new Method("getMemberId", Types
			.getTypeStringForMethod(Types.INT, new String[] { Types.INT,
					Types.getAsInternalType(ClassNames.CLASS_SLASH) }));
	
	public static final Method addOrRemoveRole = new Method("_OT$addOrRemoveRole", Types
			.getTypeStringForMethod(Types.VOID, new String[] { Types.getAsInternalType(ClassNames.OBJECT_SLASH), Types.BOOLEAN }));

	public static boolean isReflectiveOTMethod(String methodName, String methodDescriptor) {
		if ((methodName.equals("hasRole") && methodDescriptor.equals("(Ljava/lang/Object;)Z"))
				|| (methodName.equals("hasRole") && methodDescriptor.equals("(Ljava/lang/Object;Ljava/lang/Class;)Z"))
				|| (methodName.equals("getRole") && methodDescriptor.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))
				|| (methodName.equals("getRole") && methodDescriptor.equals("(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"))
				|| (methodName.equals("getAllRoles") && methodDescriptor.equals("()[Ljava/lang/Object;"))
				|| (methodName.equals("getAllRoles") && methodDescriptor.equals("(Ljava/lang/Class;)[Ljava/lang/Object;"))
				|| (methodName.equals("unregisterRole") && methodDescriptor.equals("(Ljava/lang/Object;)V"))
				|| (methodName.equals("unregisterRole") && methodDescriptor.equals("(Ljava/lang/Object;Ljava/lang/Class;)V"))
		   )
			return true;
		return false;
	}

}
