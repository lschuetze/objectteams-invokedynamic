/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2011, 2016 GK Software AG and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 *		Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otredyn.runtime;

/**
 * Interface through which the {@link TeamManager} reaches into the OTREDyn.
 * Representation of a callin binding or a callout decapsulation binding.
 * 
 * @author stephan
 */
public interface IBinding {

	public static enum BindingType {
		CALLIN_BINDING, FIELD_ACCESS, METHOD_ACCESS, ROLE_BASE_BINDING
	}

	public final static short CALLIN_BASE = 1;
	public final static short STATIC_BASE = 2;
	public final static short FINAL_BASE = 4;
	public final static short PRIVATE_BASE = 8;

	public static enum CallinModifier {
		BEFORE(1), REPLACE(2), AFTER(3);

		private final int ord;

		CallinModifier(int ord) {
			this.ord = ord;
		}

		public static CallinModifier fromString(String s) {
			switch (s) {
			case "before":
				return BEFORE;
			case "after":
				return AFTER;
			case "replace":
				return REPLACE;
			}
			// Should not reach
			throw new IllegalArgumentException("Invalid callin modifier string in bytecode: " + s);
		}

		public int getCallinModifierValue() {
			return ord;
		}
	}

	BindingType getType();

	/** The base class as declared in the source level playedBy clause. */
	String getBoundClass();

	/** The base class actually declaring the referenced member. */
	String getDeclaringBaseClassName();

	/** Name of the bound base member. */
	String getMemberName();

	/** Signature (JVM encoding) of the bound base member. */
	String getMemberSignature();

	/** @since 2.7 */
	String getRoleClassName();
	
	/** @since 2.7 */
	String getRoleMethodName();
	
	/** @since 2.7 */
	String getRoleMethodSignature();

	/** Answer flags describing the base method (static, private, final, callin). */
	int getBaseFlags();

	/** Answer the ID (callinId or perTeamAccessId) */
	int getPerTeamId();

	/** Does base method matching include overrides with covariant return type?. */
	boolean isHandleCovariantReturn();

	/**
	 * Does the bound role method issue a base super call (needing to be handled in
	 * __OT$callOrig)?
	 * 
	 * @since 2.5
	 */
	boolean requiresBaseSuperCall();

	CallinModifier getCallinModifier();
}
