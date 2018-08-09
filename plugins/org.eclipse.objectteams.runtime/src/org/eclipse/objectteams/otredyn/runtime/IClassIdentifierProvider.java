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
package org.eclipse.objectteams.otredyn.runtime;

/**
 * This class provides globally unique identifiers for all classes.
 * @author Oliver Frank
 */
public interface IClassIdentifierProvider {
	/**
	 * Returns a globally unique identifier for a base class of a given team.
	 * @param teem
	 * @param boundClassname
	 * @return
	 */
	public String getBoundClassIdentifier(Class<?> teem, String boundClassname);

	/**
	 * Returns a globally unique identifier for a superclass of the class
	 * with the given id.
	 * @param classId
	 * @param superclassName
	 * @return
	 */
	public String getSuperclassIdentifier(String classId, String superclassName);
	
	/**
	 * Returns a globally unique identifier for a class.
	 * @param clazz
	 * @return
	 */
	public String getClassIdentifier(Class<?> clazz);
}
