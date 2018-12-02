package org.eclipse.objectteams.otredyn.runtime.dynamic.linker.support;

import java.util.HashMap;
import java.util.Map;

public final class ObjectTeamsTypeUtilities {

	public static final Map<String, Class<?>> TYPE_CACHE = new HashMap<>();

	private static final String SEPARATOR = "$__OT__";
	private static final String ITF_SEPARATOR = "$";

	public static Class<?> getRoleImplementationType(String roleName, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + SEPARATOR + roleName;

		if (TYPE_CACHE.containsKey(roleClassName)) {
			return TYPE_CACHE.get(roleClassName);
		}

		try {
			Class<?> clazz = Class.forName(roleClassName, true, teamClass.getClassLoader());
			TYPE_CACHE.put(roleClassName, clazz);
			return clazz;
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}

	public static Class<?> getRoleInterfaceType(String roleName, Class<?> teamClass) {
		final String roleClassName = teamClass.getName() + ITF_SEPARATOR + roleName;
		
		if(TYPE_CACHE.containsKey(roleClassName)) {
			return TYPE_CACHE.get(roleClassName);
		}
		
		try {
			Class<?> clazz = Class.forName(roleClassName, true, teamClass.getClassLoader());
			TYPE_CACHE.put(roleClassName, clazz);
			return clazz;
		} catch (ClassNotFoundException e) {
			NoSuchMethodError ee = new NoSuchMethodError();
			ee.initCause(e);
			throw ee;
		}
	}
}
