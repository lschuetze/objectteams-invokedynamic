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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.objectteams.otredyn.runtime.IBoundClass;
import org.eclipse.objectteams.otredyn.runtime.IMember;
import org.objectweb.asm.Opcodes;

/**
 * Represents a member of a class.
 * This class abstract. Instances of this class can only be created
 * as {@link Field} or {@link Method}.
 * @author Oliver Frank
 */
public abstract class Member implements IMember {

	/**
	 * Map of globally unique Ids by keys that are constructed from the resolved target member.
	 * NOTE that internally we temporarily set bit 0x80000000 to signal that a member is a constructor.
	 * See https://bugs.eclipse.org/494415
	 */
	private static Map<String, Integer> idMap = new HashMap<String, Integer>();
	private static int currentId = 1; // distinguish from uninitialized 0 (for debugging purposes)
	
	private String name;
	private String signature;
	private boolean isStatic;
	private int accessFlags;

	public Member(String name, String signature) {
		this.name = name;
		this.signature = signature;
	}

	public Member(String name, String signature, boolean isStatic, int accessFlags) {
		this(name, signature);
		this.isStatic = isStatic;
		this.accessFlags = accessFlags;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public String getName() {
		return name;
	}

	public String getSignature() {
		return signature;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}
	
	public boolean isPrivate() {
		return (this.accessFlags & Opcodes.ACC_PRIVATE) != 0;
	}
	
	public int getAccessFlags() {
		return this.accessFlags;
	}
	
	/**
	 * Returns a globally unique id for a given key.
	 * @param key
	 * @return
	 */
	protected int getId(String key) {
		Integer id = idMap.get(key);
		if (id == null) {
			synchronized (idMap) {
				int thisId = currentId;
				currentId += 2; // 2-increment to keep space for base-super calls
				idMap.put(key, thisId);
				return thisId;
			}
		}

		return id;
	}
	
	public abstract int getGlobalId(IBoundClass clazz);

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("0x").append(Integer.toHexString(this.accessFlags)).append(' ');
		buf.append(this.name).append(this.signature);
		return buf.toString();
	}
}
