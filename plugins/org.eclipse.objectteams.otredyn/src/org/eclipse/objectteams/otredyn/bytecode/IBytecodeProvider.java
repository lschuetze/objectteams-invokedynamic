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

/**
 * Interface for classes, that provide bytecode for other classes
 * @author Oliver Frank
 */
public interface IBytecodeProvider {
	/**
	 * Returns the bytecode of the class with the given id.
	 * Attention: This may be an expensive operation 
	 * (e.g. if the bytecode must be read from the hard disk).
	 * @param classId
	 * @return the bytecode of the class or null, if the class
	 * was not loaded yet. 
	 */
	public byte[] getBytecode(String classId);
	
	/**
	 * Sets the bytecode of the class with the given id
	 * in the BytecodeProvider
	 * Attention: This may be an expensive operation 
	 * (e.g. if the bytecode is written to the hard disk).
	 * @param classId
	 * @param bytecode
	 */
	public void setBytecode(String classId, byte[] bytecode);
}
