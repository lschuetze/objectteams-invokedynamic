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

import java.lang.instrument.UnmodifiableClassException;

/**
 * Classes that implements this interface can redefine classes at runtime.
 * @author Oliver Frank
 */
public interface IRedefineStrategy {
	
	/**
	 * Redefines a class at runtime. 
	 * @param clazz the existing class instance
	 * @param bytecode the new bytecode for this class 
	 * @throws ClassNotFoundException the class was not loaded yet
	 * @throws UnmodifiableClassException it is impossible to redefine the class
	 */
	public void redefine(Class<?> clazz, byte[] bytecode) throws ClassNotFoundException, UnmodifiableClassException;
}
