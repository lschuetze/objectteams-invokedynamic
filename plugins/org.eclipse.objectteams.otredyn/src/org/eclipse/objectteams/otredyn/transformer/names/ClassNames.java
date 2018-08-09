/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2014 Oliver Frank and others.
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
package org.eclipse.objectteams.otredyn.transformer.names;

import java.util.HashSet;
import java.util.List;

import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.objectteams.IBoundBase2;
import org.objectteams.ILiftingParticipant;
import org.objectteams.ITeam;
import org.objectteams.ImplicitTeamActivation;
import org.objectteams.LiftingFailedException;
import org.objectteams.LiftingVetoException;
import org.objectteams.SneakyException;
import org.objectteams.Team;
import org.objectteams.TeamThreadManager;
import org.objectteams.WrongRoleException;

/**
 * Container for class names used in the bytecode manipulating classes
 * @author Oliver Frank
 */
public abstract class ClassNames {
	public final static String I_BOUND_BASE_SLASH = IBoundBase2.class.getName().replace('.', '/');
	public final static String OBJECT_SLASH = Object.class.getName().replace('.', '/');
	public final static String CLASS_SLASH = Class.class.getName().replace('.', '/');
	public final static String I_BOUND_BASE_DOT = IBoundBase2.class.getName();
	public final static String TEAM_MANAGER_SLASH = TeamManager.class.getName().replace('.', '/');
	public final static String ITEAM_SLASH = ITeam.class.getName().replace('.', '/');
	public final static String TEAM_SLASH = Team.class.getName().replace('.', '/');
	public final static String LIST_SLASH = List.class.getName().replace('.', '/');
	public final static String HASH_SET_SLASH = HashSet.class.getName().replace('.', '/');
	public final static String IMPLICIT_ACTIVATION = ImplicitTeamActivation.class.getName().replace('.', '/');
	public final static String ILIFTING_PARTICIPANT = ILiftingParticipant.class.getName().replace('.', '/');
	public static final String WRONG_ROLE_EXCEPTION = WrongRoleException.class.getName().replace('.', '/');
	public static final String LIFTING_FAILED_EXCEPTION = LiftingFailedException.class.getName().replace('.', '/');
	public static final String LIFTING_VETO_EXCEPTION = LiftingVetoException.class.getName().replace('.', '/');

	public static final String SNEAKY_EXCEPTION_SLASH = SneakyException.class.getName().replace('.', '/');
	// member of SneakyException:
	public static final String RETHROW_SELECTOR = "rethrow";
	public static final String RETHROW_SIGNATURE = "()V"; 
	
	public static final String THREAD_SLASH = Thread.class.getName().replace('.', '/');
	public static final String TEAM_THREAD_MANAGER_SLASH = TeamThreadManager.class.getName().replace('.', '/');
	public static final String THROWABLE_SLASH = Throwable.class.getName().replace('.', '/');
	public static final String RUNNABLE_SLASH = Runnable.class.getName().replace('.', '/');
}
