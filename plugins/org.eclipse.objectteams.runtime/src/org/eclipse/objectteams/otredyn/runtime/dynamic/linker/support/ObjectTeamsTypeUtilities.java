package org.eclipse.objectteams.otredyn.runtime.dynamic.linker.support;

import org.eclipse.objectteams.otredyn.runtime.IBinding;

public final class ObjectTeamsTypeUtilities {

	private static final String SEPARATOR = "$__OT__";
	private static final String ITF_SEPARATOR = "$";

	public static Class<?> getRoleImplementationType(IBinding binding, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + SEPARATOR + binding.getRoleClassName().intern();
		try {
			return Class.forName(roleClassName, true, teamClass.getClassLoader());
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static Class<?> getRoleInterfaceType(IBinding binding, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + ITF_SEPARATOR + binding.getRoleClassName().intern();
		try {
			return Class.forName(roleClassName, true, teamClass.getClassLoader());
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}

}
