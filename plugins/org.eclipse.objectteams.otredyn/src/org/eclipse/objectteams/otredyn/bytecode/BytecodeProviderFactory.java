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
 * This class creates and provides a singleton instance of
 * IBytecodeProvider. 
 * Actually it returns a instance of InMemeoryBytecodeProvider.
 * In the future, this class should decides (e.g. by a system property), 
 * which BytecodeProvider should be used   
 * @author Oliver Frank
 */
public class BytecodeProviderFactory {
	private static IBytecodeProvider instance;
	public static synchronized IBytecodeProvider getBytecodeProvider() {
		if (instance == null) {
			instance = new InMemoryBytecodeProvider();
		}
		return instance;
	}
}
