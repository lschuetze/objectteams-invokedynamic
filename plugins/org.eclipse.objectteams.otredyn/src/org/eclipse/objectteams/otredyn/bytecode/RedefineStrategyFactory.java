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
 * Provides a singleton instance of {@link IRedefineStrategy}.
 * If no instance was set, this class returns the {@link OtreRedefineStrategy} 
 * @author Oliver Frank
 */
public class RedefineStrategyFactory {
	private static IRedefineStrategy redefineStrategy;

	public static IRedefineStrategy getRedefineStrategy() {
		if (redefineStrategy == null) {
			redefineStrategy = new OtreRedefineStrategy();
		}
		return redefineStrategy;
	}

	public static void setRedefineStrategy(IRedefineStrategy redefineStrategy) {
		RedefineStrategyFactory.redefineStrategy = redefineStrategy;
	}
}
