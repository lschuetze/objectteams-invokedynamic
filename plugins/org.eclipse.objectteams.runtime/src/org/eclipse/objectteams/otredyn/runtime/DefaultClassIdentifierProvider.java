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
 * Default implementation of {@link IClassIdentifierProvider}, that just 
 * returns the class name as identifier. This implementation could be used
 * in a standard java context.
 * @author Oliver Frank
 */
public class DefaultClassIdentifierProvider implements IClassIdentifierProvider {

	public String getBoundClassIdentifier(Class<?> teem, String boundClassname) {
		return boundClassname;
	}

	public String getSuperclassIdentifier(String classId, String superclassName) {
		return superclassName;
	}

	public String getClassIdentifier(Class<?> clazz) {
		return clazz.getName().replace('.', '/');
	}
}
