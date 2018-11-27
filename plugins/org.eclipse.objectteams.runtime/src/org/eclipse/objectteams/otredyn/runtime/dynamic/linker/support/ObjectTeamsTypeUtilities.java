package org.eclipse.objectteams.otredyn.runtime.dynamic.linker.support;

import org.eclipse.objectteams.otredyn.runtime.IBinding;

public final class ObjectTeamsTypeUtilities {

	private static final String SEPARATOR = "$__OT__";
	private static final String ITF_SEPARATOR = "$";

	public static Class<?> getRoleImplementationType(String roleName, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + SEPARATOR + roleName;
		try {
			return Class.forName(roleClassName, true, teamClass.getClassLoader());
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static Class<?> getRoleInterfaceType(String roleName, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + ITF_SEPARATOR + roleName;
		try {
			return Class.forName(roleClassName, true, teamClass.getClassLoader());
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}
	
}
