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
package org.eclipse.objectteams.otredyn.bytecode;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is an implementaion of IBytecodeProvider
 * It keeps the bytecode of all classes in memory.
 * @author Oliver Frank
 */
public class InMemoryBytecodeProvider implements IBytecodeProvider {

	private Map<String, byte[]> bytecodeMap = new HashMap<String, byte[]>();
	
	/**
	 * Returns the bytecode of a class with the given id.
	 * In this implementation, this is not an expensive operation. 
	 * @return the bytecode of the class or null, if the class
	 * was not loaded yet.
	 */
	public byte[] getBytecode(String className) {
		return bytecodeMap.get(className);
	}

	/**
	 * Sets the bytecode of the class with the given id
	 * in the BytecodeProvider
	 * In this implementation, this is not an expensive operation.
	 * @param classId
	 * @param bytecode
	 */
	public void setBytecode(String className, byte[] bytecode) {
		bytecodeMap.put(className, bytecode);
	}
}
