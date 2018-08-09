/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 * 
 * Copyright 2015 GK Software AG
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 * 	  Stephan Herrmann - Initial API and implementation
 */
package org.eclipse.objectteams.runtime;

import java.lang.instrument.IllegalClassFormatException;

/**
 * Protocol for re-weaving a class that has already been defined.
 * Each implementation instance represents the task for one given class.
 */
public interface IReweavingTask {
	/**
	 * Reweave the class represented by this task.
	 * @param definedClass for convenience the previously defined class can
	 * 	be passed in as an argument.
	 * @throws IllegalClassFormatException various problems during bytecode transformation, e.g.,
	 * 	unexpected / illegal byte codes (like RET) encountered.
	 */
	public void reweave(Class<?> definedClass) throws IllegalClassFormatException;
}
