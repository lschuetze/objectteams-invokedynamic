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
package org.eclipse.objectteams.otredyn.bytecode;

import org.eclipse.objectteams.otredyn.runtime.IBoundClass;

/**
 * Represents a field of a class
 * @author Oliver Frank
 */
public class Field extends Member {

	public Field(String name, String signature) {
		super(name, signature);
	}
	
	public Field(String name, String signature, boolean isStatic, int accessFlags) {
		super(name, signature, isStatic, accessFlags);
	}

	/**
	 * Returns a globally unique id for the field
	 * @param clazz
	 * @return 
	 */
	@Override
	public int getGlobalId(IBoundClass clazz) {
		String key = null;
		key = clazz.getId() + getName() + getSignature();
		return getId(key);
	}
}
