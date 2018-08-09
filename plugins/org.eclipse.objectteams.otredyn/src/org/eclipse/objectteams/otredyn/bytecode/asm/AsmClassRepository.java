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
package org.eclipse.objectteams.otredyn.bytecode.asm;

import org.eclipse.objectteams.otredyn.bytecode.AbstractTeam;
import org.eclipse.objectteams.otredyn.bytecode.ClassRepository;
import org.eclipse.objectteams.otredyn.bytecode.IBytecodeProvider;


/**
 * Creates Instances of {@link AsmWritableBoundClass} as {@link AbstractTeam}
 * @author Oliver Frank
 */
public class AsmClassRepository extends ClassRepository {

	
	@Override
	protected AbstractTeam createClass(String name, String id, IBytecodeProvider bytecodeProvider, ClassLoader loader) {
		return new AsmWritableBoundClass(name, id, bytecodeProvider, loader);
	}
}
