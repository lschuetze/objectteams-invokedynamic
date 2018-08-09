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
package org.eclipse.objectteams.otredyn.bytecode;

/**
 * Helper class to handle type strings used in the bytecode
 * @author Oliver Frank
 */
public abstract class Types {

	public final static int TEAM_FLAG  = 1; // within OTClassFlags attribute
	public final static int ROLE_FLAG = 2; // within OTClassFlags attribute
    
	public static final String VOID = "V";

	public static final String BOOLEAN = "Z";

	public static final String CHAR = "C";

	public static final String BYTE = "B";

	public static final String SHORT = "S";

	public static final String INT = "I";

	public static final String FLOAT = "F";

	public static final String LONG = "J";

	public static final String DOUBLE = "D";

	private static final String ARRAY = "[";

	public static String getAsArrayType(String typeName) {
		if (typeName.length() == 1) {
			switch (typeName.charAt(0)) {
			case 'Z': case 'C': case 'B': case 'S': case 'I': case 'F': case 'J': case 'D': 
				return ARRAY + typeName;
			}
		}
		
		return ARRAY + "L" + typeName + ';';
	}
	
	public static String getAsInternalType(String className) {
		return "L" + className + ';';
	}
	
	public static String getAsInternalType(Class<?> clazz) {
		String type = null;
		if (clazz.isPrimitive()) {
			String name = clazz.getName();
			if (name.compareTo("void") == 0) {
				type = VOID;
			} else if (name.compareTo("boolean") == 0) {
				type = BOOLEAN;
			} else if (name.compareTo("char") == 0) {
				type = CHAR;
			} else if (name.compareTo("byte") == 0) {
				type = BYTE;
			} else if (name.compareTo("short") == 0) {
				type = SHORT;
			} else if (name.compareTo("int") == 0) {
				type = INT;
			} else if (name.compareTo("float") == 0) {
				type = FLOAT;
			} else if (name.compareTo("long") == 0) {
				type = LONG;
			} else if (name.compareTo("double") == 0) {
				type = DOUBLE;
			}
		} else {
			type = clazz.getName().replace('.', '/');
			if (!clazz.isArray()) {
				type = "L" + type + ';';
			}
		}
		
		return type;
	}
	
	public static String getTypeStringForMethod(String returnType, String[] paramTypes) {
		StringBuilder buf = new StringBuilder();
		buf.append('(');
		if (paramTypes != null) {
			for (String paramType : paramTypes)
				buf.append(paramType);
		}
		buf.append(')');
		buf.append(returnType);
		return buf.toString();
	}
	
	public static String getTypeStringForMethod(Class<?> returnType, Class<?>[] paramTypes) {
		StringBuilder buf = new StringBuilder();
		buf.append('(');
		if (paramTypes != null) {
			for (Class<?> paramType : paramTypes)
				buf.append(getAsInternalType(paramType));
		}
		buf.append(')');
		buf.append(getAsInternalType(returnType));
		return buf.toString();
	}
}
