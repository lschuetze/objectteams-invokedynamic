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
 * Provides a singleton instance of {@link IClassIdentifierProvider}.
 * If no instance is set, this class uses the {@link DefaultClassIdentifierProvider}.
 * @author Oliver Frank
 */
public class ClassIdentifierProviderFactory {
	private static IClassIdentifierProvider instance;
	
	public static void setClassIdentifierProvider(IClassIdentifierProvider provider) {
		instance = provider;
	}
	
	public static IClassIdentifierProvider getClassIdentifierProvider() {
		if (instance == null) {
			instance = new DefaultClassIdentifierProvider();
		}
		
		return instance;
	}
}
